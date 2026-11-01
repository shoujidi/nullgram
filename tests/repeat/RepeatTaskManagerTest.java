package com.nullgram.client.repeat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 无 Android/网络依赖的行为测试；不能替代实机测试。 */
public final class RepeatTaskManagerTest {
    private static int passed;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class Clock implements RepeatTaskManager.Scheduler {
        long now;
        final List<Event> events = new ArrayList<>();
        static final class Event {
            final Runnable action; final long at;
            Event(Runnable action, long at) { this.action = action; this.at = at; }
        }
        public long now() { return now; }
        public void post(Runnable action, long delay) { events.add(new Event(action, now + delay)); }
        public void cancel(Runnable action) { events.removeIf(event -> event.action == action); }
        void advance(long delta) {
            long end = now + delta;
            while (true) {
                Event next = null;
                for (Event event : events) if (event.at <= end && (next == null || event.at < next.at)) next = event;
                if (next == null) break;
                events.remove(next); now = Math.max(now, next.at); next.action.run();
            }
            now = end;
        }
    }
    private static final class Sender implements RepeatTaskManager.Sender {
        int calls; int id = -1; int groupSize = 1; String denied; boolean throwsError;
        List<Integer> last = Collections.emptyList();
        public String validate() { return denied; }
        public List<Integer> send() {
            calls++;
            if (throwsError) throw new IllegalStateException();
            last = new ArrayList<>();
            for (int i = 0; i < groupSize; i++) last.add(id--);
            return last;
        }
    }
    private static RepeatTaskManager manager(Clock clock) { return new RepeatTaskManager(2, clock); }
    private static boolean start(RepeatTaskManager manager, Sender sender, int n) {
        return manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, n, 3000, sender);
    }
    private static void confirm(RepeatTaskManager manager, Sender sender) {
        for (int id : sender.last) manager.onConfirmed(id);
    }
    private static void test(String name, Runnable action) {
        action.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        test("cancel before first submission queues no message", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            check(start(manager, sender, 5), "start accepted");
            check(sender.calls == 0 && manager.getProgress().submitted == 0, "start is not submission");
            manager.cancelTask(); clock.advance(600000);
            check(sender.calls == 0 && clock.events.isEmpty(), "no message queued after early cancellation");
        });
        for (RepeatTask.Type type : RepeatTask.Type.values())
        for (int n : Arrays.asList(1, 5, 10, 100)) test(type + " exactly " + n + " rounds", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            check(manager.startTask(123, type, n, 3000, sender), "start");
            for (int i = 0; i < n; i++) {
                clock.advance(i == 0 ? 0 : 3000);
                check(sender.calls == i + 1, "one submission per tick");
                check(manager.getProgress().confirmed == i, "not confirmed prematurely");
                confirm(manager, sender);
            }
            clock.advance(600000);
            check(sender.calls == n, "no N+1 submission");
            check(manager.getTaskState() == RepeatTaskState.COMPLETED, "completed after ACK");
            check(clock.events.isEmpty(), "scheduler released");
        });
        for (long interval : new long[]{10, 15, 1000, 1750, 60000}) test("exact interval " + interval + " ms", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            check(manager.startTask(123, RepeatTask.Type.EXISTING, 3, interval, sender), "start");
            clock.advance(0);
            check(sender.calls == 1 && clock.now == 0, "first submission is immediate");
            confirm(manager, sender);
            clock.advance(interval - 1);
            check(sender.calls == 1, "no submission before deadline");
            clock.advance(1);
            check(sender.calls == 2 && clock.now == interval, "second at exact deadline");
            confirm(manager, sender);
            clock.advance(interval - 1);
            check(sender.calls == 2, "third not early");
            clock.advance(1);
            check(sender.calls == 3 && clock.now == 2 * interval, "third at exact deadline");
            confirm(manager, sender); clock.advance(600000);
            check(sender.calls == 3 && manager.getTaskState() == RepeatTaskState.COMPLETED, "no extra submission");
        });
        test("delayed scheduler submits once without catch-up burst", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(0);
            clock.now += 10000; clock.advance(0);
            check(sender.calls == 2, "one delayed submission without replaying missed deadlines");
            confirm(manager, sender); clock.advance(2999);
            check(sender.calls == 2, "interval measured from actual submission");
            clock.advance(1); check(sender.calls == 3, "next deadline");
        });
        test("submit every interval without waiting for ACK and correlate out-of-order ACKs", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            check(manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 100, 10, sender), "start");
            clock.advance(990);
            check(sender.calls == 100 && manager.getProgress().confirmed == 0, "100 submissions independent of ACKs");
            check(manager.getTaskState() == RepeatTaskState.RUNNING, "not completed until confirmed");
            manager.onConfirmed(-999); manager.onConfirmed(-100); manager.onConfirmed(-100);
            clock.advance(10);
            check(sender.calls == 100 && manager.getProgress().confirmed == 1, "duplicates cannot schedule extra sends");
            for (int id = -99; id >= -100; id--) manager.onConfirmed(id);
            for (int id = -1; id >= -98; id--) manager.onConfirmed(id);
            check(manager.getTaskState() == RepeatTaskState.COMPLETED, "all distinct ACKs complete");
            check(manager.getProgress().submittedMessages == 100 && manager.getProgress().confirmedMessages == 100, "accurate totals");
            check(clock.events.isEmpty(), "all callbacks released");
        });
        test("stop immediately and repeated stop is harmless", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(0); confirm(manager, sender);
            manager.cancelTask(); manager.cancelTask(); clock.advance(600000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.CANCELLED, "stopped");
            check(clock.events.isEmpty(), "cancel removed callbacks");
        });
        test("cancel one millisecond before due submission", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(0); confirm(manager, sender); clock.advance(2999);
            manager.cancelTask(); clock.advance(600000);
            check(sender.calls == 1 && clock.events.isEmpty(), "cancel prevents next submission at boundary");
        });
        for (boolean stopFirst : new boolean[]{true, false}) test("same deadline stop first=" + stopFirst, () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5);
            if (stopFirst) clock.post(manager::cancelTask, 3000);
            clock.advance(0);
            confirm(manager, sender);
            if (!stopFirst) clock.post(manager::cancelTask, 3000);
            clock.advance(3000);
            check(sender.calls == (stopFirst ? 1 : 2), "only a submission that preceded stop is permitted");
            confirm(manager, sender); clock.advance(600000);
            check(sender.calls == (stopFirst ? 1 : 2) && manager.getTaskState() == RepeatTaskState.CANCELLED, "late ACK cannot restart cancelled task");
            check(clock.events.isEmpty(), "all callbacks removed");
        });
        test("cancel pending round then restart ignores old ACK and error", () -> {
            Clock clock = new Clock(); Sender first = new Sender(), second = new Sender();
            second.id = -100;
            RepeatTaskManager manager = manager(clock);
            start(manager, first, 5); clock.advance(0); int oldId = first.last.get(0);
            manager.cancelTask();
            check(manager.startTask(456, RepeatTask.Type.EXISTING, 1, 1000, second), "restart accepted");
            clock.advance(0); manager.onConfirmed(oldId); manager.onSendError(oldId, "FLOOD_WAIT_60");
            check(manager.getTaskState() == RepeatTaskState.RUNNING && manager.getProgress().confirmed == 0, "old notifications ignored");
            confirm(manager, second); clock.advance(600000);
            check(first.calls == 1 && second.calls == 1 && manager.getTaskState() == RepeatTaskState.COMPLETED, "restart completed");
            check(manager.getProgress().dialogId == 456 && clock.events.isEmpty(), "new target and cleanup");
        });
        test("conflicting start does not replace target", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5);
            check(!manager.startTask(456, RepeatTask.Type.EXISTING, 10, 1000, new Sender()), "reject concurrent start");
            check(manager.getProgress().dialogId == 123 && manager.getProgress().account == 2, "immutable target");
        });
        test("group ACKs and separate round/message counts", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); sender.groupSize = 3;
            RepeatTaskManager manager = manager(clock); start(manager, sender, 1); clock.advance(0);
            manager.onConfirmed(sender.last.get(0));
            check(manager.getTaskState() == RepeatTaskState.RUNNING && manager.getProgress().confirmed == 0, "partial group");
            confirm(manager, sender);
            check(manager.getProgress().submittedMessages == 3 && manager.getProgress().confirmedMessages == 3, "group counts");
        });
        test("overlapping groups confirm independently out of order", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); sender.groupSize = 3;
            RepeatTaskManager manager = manager(clock);
            manager.startTask(123, RepeatTask.Type.EXISTING, 2, 10, sender); clock.advance(10);
            check(sender.calls == 2 && manager.getProgress().submittedMessages == 6, "two groups submitted without ACKs");
            manager.onConfirmed(-4); manager.onConfirmed(-5); manager.onConfirmed(-6);
            check(manager.getProgress().confirmed == 1 && manager.getProgress().confirmedMessages == 3, "later group complete first");
            manager.onConfirmed(-1); manager.onConfirmed(-1); manager.onConfirmed(-3);
            check(manager.getProgress().confirmed == 1 && manager.getProgress().confirmedMessages == 5, "partial and duplicate ACKs");
            manager.onConfirmed(-2);
            check(manager.getTaskState() == RepeatTaskState.COMPLETED && manager.getProgress().confirmed == 2, "both groups confirmed");
            check(clock.events.isEmpty(), "timeout removed");
        });
        test("oldest unconfirmed round timeout is not extended by newer sends or partial ACKs", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); sender.groupSize = 2;
            RepeatTaskManager manager = manager(clock);
            manager.startTask(123, RepeatTask.Type.EXISTING, 100, 10, sender);
            clock.advance(RepeatConfig.CONFIRMATION_TIMEOUT_MS - 1);
            manager.onConfirmed(-1);
            check(manager.getTaskState() == RepeatTaskState.RUNNING && sender.calls == 100, "original deadline not yet reached");
            clock.advance(1);
            check(manager.getTaskState() == RepeatTaskState.FAILED && "CONFIRMATION_TIMEOUT".equals(manager.getProgress().error), "original deadline preserved");
            check(manager.getProgress().confirmedMessages == 1 && manager.getProgress().confirmed == 0, "partial group not complete");
            check(clock.events.isEmpty(), "callbacks removed");
        });
        test("timeout advances to next round after earliest round is confirmed", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 2); clock.advance(3000); manager.onConfirmed(-1);
            clock.advance(RepeatConfig.CONFIRMATION_TIMEOUT_MS - 3000);
            check(manager.getTaskState() == RepeatTaskState.RUNNING, "first deadline cancelled");
            clock.advance(2999); check(manager.getTaskState() == RepeatTaskState.RUNNING, "second deadline not reached");
            clock.advance(1); check(manager.getTaskState() == RepeatTaskState.FAILED, "second original deadline reached");
        });
        test("error on earlier pending round stops future submissions", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 100, 10, sender); clock.advance(20);
            manager.onSendError(-1, "FLOOD_WAIT_60"); clock.advance(600000);
            check(sender.calls == 3 && manager.getTaskState() == RepeatTaskState.FAILED, "no submissions after error");
            check("FLOOD_WAIT_60".equals(manager.getProgress().error) && clock.events.isEmpty(), "reason and cleanup");
        });
        test("cancel with multiple pending rounds ignores late notifications", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 100, 10, sender); clock.advance(25);
            manager.cancelTask(); manager.onConfirmed(-1); manager.onSendError(-2, "SEND_FAILED"); clock.advance(600000);
            check(sender.calls == 3 && manager.getTaskState() == RepeatTaskState.CANCELLED, "cancel remains terminal");
            check(manager.getProgress().confirmed == 0 && clock.events.isEmpty(), "no false completion or pending callbacks");
        });
        test("listener restart cannot inherit old scheduling", () -> {
            Clock clock = new Clock(); Sender first = new Sender(), second = new Sender(); second.id = -100;
            RepeatTaskManager manager = manager(clock);
            manager.addListener(task -> {
                if (task != null && task.state == RepeatTaskState.RUNNING && task.dialogId == 123 && task.submitted == 1) {
                    manager.cancelTask();
                    manager.startTask(456, RepeatTask.Type.NEW_MESSAGE, 2, 1000, second);
                }
            });
            manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 5, 10, first); clock.advance(999);
            check(first.calls == 1 && second.calls == 1, "old tick not attached to replacement task");
            clock.advance(1); check(second.calls == 2, "new task uses its own interval");
            manager.cancelTask(); check(clock.events.isEmpty(), "replacement cleanup");
        });
        test("FLOOD_WAIT stops without retry", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(0); manager.onSendError(sender.last.get(0), "FLOOD_WAIT_60");
            clock.advance(600000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.FAILED, "no retry");
            check("FLOOD_WAIT_60".equals(manager.getProgress().error), "reason retained");
        });
        test("offline/ACK timeout does not report success", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(RepeatConfig.CONFIRMATION_TIMEOUT_MS);
            check(sender.calls == 5 && manager.getTaskState() == RepeatTaskState.FAILED, "bounded timeout without resubmission");
            check(manager.getProgress().confirmedMessages == 0, "unconfirmed count");
        });
        test("permission rechecked before every round", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 5); clock.advance(0); confirm(manager, sender); sender.denied = "SLOWMODE_WAIT";
            clock.advance(3000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.FAILED, "permission changed");
        });
        test("exception does not resubmit", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); sender.throwsError = true;
            RepeatTaskManager manager = manager(clock); start(manager, sender, 5); clock.advance(600000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.FAILED, "one attempt");
        });
        test("ordinary send error cannot stop repeat task", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            start(manager, sender, 1); clock.advance(0);
            manager.onSendError(-999, "CHAT_WRITE_FORBIDDEN"); manager.onConfirmed(-999);
            check(manager.getTaskState() == RepeatTaskState.RUNNING && manager.getProgress().confirmedMessages == 0, "unrelated notifications ignored");
            manager.onSendError(sender.last.get(0), "CHAT_WRITE_FORBIDDEN"); clock.advance(600000);
            check(manager.getTaskState() == RepeatTaskState.FAILED && sender.calls == 1, "own error stops without retry");
            check(manager.getProgress().confirmedMessages == 0 && clock.events.isEmpty(), "no false success or callbacks");
        });
        test("empty upstream submission fails without false counts", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); sender.groupSize = 0;
            RepeatTaskManager manager = manager(clock); start(manager, sender, 5); clock.advance(600000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.FAILED, "no retry");
            check(manager.getProgress().submitted == 0 && manager.getProgress().confirmed == 0, "no false progress");
            check(clock.events.isEmpty(), "no callbacks retained");
        });
        test("validation failure before first send and account switch before next round", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            sender.denied = "OFFLINE";
            check(!start(manager, sender, 5), "offline start refused"); clock.advance(600000);
            check(sender.calls == 0 && manager.getTaskState() == RepeatTaskState.FAILED, "no offline sends");
            sender.denied = null; check(start(manager, sender, 5), "restart after reconnect");
            clock.advance(0); confirm(manager, sender); sender.denied = "ACCOUNT_CHANGED";
            clock.advance(600000);
            check(sender.calls == 1 && manager.getTaskState() == RepeatTaskState.FAILED, "account switch stops next round");
        });
        test("progress listeners see accurate completion and release", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            List<RepeatTask> changes = new ArrayList<>();
            RepeatTaskManager.Listener listener = changes::add; manager.addListener(listener);
            start(manager, sender, 1);
            check(changes.get(changes.size() - 1).remaining() == 1, "initial remaining");
            clock.advance(0); RepeatTask submitted = changes.get(changes.size() - 1);
            check(submitted.submitted == 1 && submitted.remaining() == 0 && submitted.confirmed == 0, "submission progress");
            confirm(manager, sender); RepeatTask complete = changes.get(changes.size() - 1);
            check(complete.state == RepeatTaskState.COMPLETED && complete.confirmed == 1, "terminal notification");
            check(submitted.state == RepeatTaskState.RUNNING && submitted.confirmed == 0, "prior snapshots immutable");
            int size = changes.size(); manager.removeListener(listener); manager.cleanupTask();
            check(changes.size() == size && manager.getProgress() == null && clock.events.isEmpty(), "listener and resources released");
        });
        test("account isolation", () -> {
            Clock clock = new Clock(); RepeatTaskManager a = new RepeatTaskManager(0, clock), b = new RepeatTaskManager(1, clock);
            Sender sa = new Sender(), sb = new Sender(); start(a, sa, 5); start(b, sb, 5); clock.advance(0);
            a.cancelTask(); confirm(b, sb); clock.advance(3000);
            check(sa.calls == 1 && sb.calls == 2, "account scoped cancellation");
        });
        test("parameter bounds and cleanup", () -> {
            Clock clock = new Clock(); Sender sender = new Sender(); RepeatTaskManager manager = manager(clock);
            for (int count : new int[]{0, -1, 101}) check(!start(manager, sender, count), "invalid count");
            check(!manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 5, 9, sender), "interval below minimum");
            check(!manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 5, 60001, sender), "interval above maximum");
            check(!manager.startTask(0, RepeatTask.Type.NEW_MESSAGE, 5, 3000, sender), "missing target");
            check(!manager.startTask(123, null, 5, 3000, sender), "missing type");
            start(manager, sender, 5); manager.cancelTask(); manager.cleanupTask();
            check(manager.getTaskState() == RepeatTaskState.IDLE && clock.events.isEmpty(), "cleanup");
        });
        System.out.println("PASS " + passed + " deterministic behavior tests (not device tests)");
    }
}
