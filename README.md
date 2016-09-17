# TCP switch

## clone

```
git clone git@github.com:senzptojects/tcp-switch.git senz-switch
```

## build with docker

```
sbt assembly  
docker build --tag erangaeb/senzswitch:0.14 .  
```

## run with docker

```
docker run -it -d -p 7070:7070 -v <logs dir>:/app/logs -v <keys dir>:/app/.keys -e MONGO_HOST=<mongo host> -e SWITCH_MODE=<switch mode> erangaeb/senzswitch:0.14
docker run -it -d -p 7070:7070 -v /home/docker/senz/switch/logs:/app/logs -v /home/docker/senz/switch/keys:/app/.keys -e MONGO_HOST=172.17.0.1 -e SWITCH_MODE=PROD erangaeb/senzswitch:0.14
```
