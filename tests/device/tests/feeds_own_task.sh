# Bug 2026-10-25 #1: the feed reader opened in the launcher's home task, so SystemUI treated it
# as home and no edge gesture (or toolbar) worked. Feeds now live in their own app and task.
unlock; home
FEEDS=com.bompotis.etesian.feeds
sh_ am start -n $FEEDS/.MainActivity >/dev/null; sleep 2
check "feeds list is not in the home task" '[ "$(task_type $FEEDS/.MainActivity)" = standard ]'
check "launcher no longer has its own feed screens" '! sh_ dumpsys package $LAUNCHER | grep -q "FeedsActivity\|FeedReaderActivity"'
home
