#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p artifacts/repeat-tests
module=TMessagesProj/src/main/java/com/nullgram/client/repeat
javac -encoding UTF-8 -d artifacts/repeat-tests "$module/RepeatConfig.java" "$module/RepeatTaskState.java" "$module/RepeatTask.java" "$module/RepeatTaskManager.java" tests/repeat/RepeatTaskManagerTest.java
java -cp artifacts/repeat-tests com.nullgram.client.repeat.RepeatTaskManagerTest
