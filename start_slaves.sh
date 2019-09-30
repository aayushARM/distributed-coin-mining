#!/bin/bash
count=0
while [ $count -lt 10 ];do
	eval "sshpass -p wrong_passwd ssh uname@luna0$count.cs.rit.edu 'killall java'"
	count=$((count+1))
done

count=0
while [ $count -lt 10 ];do
	echo "Slave $count" -e "sshpass -p wrong_passwd ssh uname@luna0$count.cs.rit.edu 'cd ./ParallelComp/ && java CoinMining_Slave 500$count'"
	gnome-terminal --title="Slave $count" -e "sshpass -p wrong_passwd ssh as2425@luna0$count.cs.rit.edu 'cd ./ParallelComp/ && java CoinMining_Slave 500$count'" 1>/dev/null
	count=$((count+1))
done