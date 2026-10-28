# Trains

A simple train network system as a alternative to minecarts this uses a graph where stations are the nodes and rail connections represent the edges each edge holding the connected station ids and distance. edges are broken by doing aync scans of all the stations I found the cost of this is worth the speed of the train network system and the fact players are no longer loading chunks during there large train station journey the server.

## Installation

1. Download or build the plugin JAR.
2. Place the shaded JAR (`Trains-1.0-all.jar`) in your Paper server's `plugins` folder.
3. Start the server. Edit `plugins/NetworkTrains/config.yml` to change the train speed, then run `/trainadmin reload`.

## Building

Install Java 21, then run:

```sh
./gradlew build
```

The shaded plugin JAR is in `build/libs`.

## Contributing

If you have any ideas for this project send a PR or fork it I don't mind

