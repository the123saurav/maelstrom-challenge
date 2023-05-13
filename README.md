# maelstrom-challenge

This is a Java implementation of maelstrom challenges.
It currently features following solutions:
- boilerplate code to be used across solutions
- echo server
- unique id generation

### Setup
We are using maven and tested with Java 20.
There is 1 maven sub-module per challenge.
I am using maven `shade` plugin to generate a `fat jar` with all dependencies which is then invoked from 
inside the `bin/<module>/server` bash script.

To generate the jar, just run:
```
mvn clean package
```


