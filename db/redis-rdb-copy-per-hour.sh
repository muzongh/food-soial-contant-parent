#!bin/bash
cur_date=$(date "+%Y%m%d%H%M%S")
rm -rf /user/local/redis/snapshotting/$cur_date
mkdir -p /user/local/redis/snapshotting/$cur_date
cp /user/local/redis/data/dump.rdb /user/local/redis/snapshotting/$cur_date

del_date=$(date -d -48hour "+%Y%m%d%H%M")
rm -rf /user/local/redis/snapshotting/$del_date
