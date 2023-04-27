./gradlew build
docker build -t jnetwork .
docker run -it --net=host --cpus=1 --memory=768m jnetwork

container_id=$(docker ps -qa --filter ancestor=jnetwork)
if [ $container_id ]; then
  docker stop $container_id
  docker rm $container_id
  docker rmi $(docker images 'jnetwork' -a -q)
fi
