./gradlew build

## docker-compose up
docker build -t jnetwork .

## --net=host
#Net interface: veth5afd9f5 - veth5afd9f5
#IP address: /fe80:0:0:0:1445:6cff:fe66:e6be%veth5afd9f5
#
#Net interface: docker0 - docker0
#IP address: /fe80:0:0:0:42:b2ff:fec1:7218%docker0
#IP address: /172.17.0.1
#
#Net interface: br-1aef6168fa68 - br-1aef6168fa68
#IP address: /192.168.80.1
#
#Net interface: br-e965599ad943 - br-e965599ad943
#IP address: /172.18.0.1
#
#Net interface: br-4504ef4fd65b - br-4504ef4fd65b
#IP address: /127.0.0.1
## docker run -it --net=host --cpus=1 --memory=768m jnetwork

## Net interface: eth0 - eth0
 #IP address: /11.11.0.2
 #
 #Net interface: lo - lo
 #IP address: /127.0.0.1
 #
 #Gateway: /11.11.0.2
 #Gateway interface: name:eth0 (eth0)
 #Gateway: B
docker run -it --net=lamatola-net --cpus=1 --memory=768m jnetwork


## wont let connect to egress at all
## docker run -it --net=none --cpus=1 --memory=768m jnetwork

## on bridge network
#Net interface: eth0 - eth0
#IP address: /172.17.0.3
#
#Net interface: lo - lo
#IP address: /127.0.0.1
#
#Gateway: /172.17.0.3
#Gateway interface: name:eth0 (eth0)

container_id=$(docker ps -qa --filter ancestor=jnetwork)
if [ $container_id ]; then
  docker stop $container_id
  docker rm $container_id
  # shellcheck disable=SC2046
  docker rmi $(docker images 'jnetwork' -a -q)
fi

docker-compose down