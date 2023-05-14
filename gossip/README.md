### What are we implementing?

Gossip is a massive subsystem to implement and hence we would be implementing a part of it.
We will not be implementing:
- Fail-stop/crash as that would need a recovery mechanism via disk/streaming snapshot.
- Failure detection of nodes.
- No empty heartbeat messaging for liveness

Since we will not inject a stop `nemesis`, we will just rely on storing things in memory.

### Design

At start, we get to know all nodes in the cluster and this view never changes i.e nodes
are not added/removed by maelstrom.

A node would need to broadcast to other nodes in 2 cases:
- external `broadcast` request
- internal `gossip` request

We will implement a version of topology aware gossip with T gossip rounds to allow for information
dissemination. Gossip needs each node reachable from 
every other node in a directed graph.

Every node also keeps track of what nodes it has heard from via RPC call directly.
If it finds it has not heard from a node for a while, it will make a "how are you" call to it
and the receiver adds this node to "preferred list" for next gossip round.

Now whenever a node needs to gossip, it first checks if the message is expired(T gossip rounds in msg header).
If yes:
    ignore.
Else:
    - acknowledge to caller(we will replicate async, works for current problem as we dont provide fail stop)
    - find K + X nodes
        - additional X subset of "preferred list" 
    - "gossip" to above nodes with timeout(say 3-4X of simulated latency)
        - if the gossip succeeds and there is a pending messages for node:
            - send work to TP to drain it(we don't need ordering here as all messages are unique)
        - else if the gossip errs/timeouts:
            - if its recoverable err:
                - add msg to pending message list
            - else:
                - add to dlq list(this most likely is data loss)

Note that we are not blocking handler thread with response processing here.

### How do we find K?
If N == 1:
    K = 1
else if N in [2, 6]:
    K = ceil[N/2]
else:
    K = 3

### How is it handling network partition?
Network partition would lead to rpc timeouts[implemented in our case as not receiving a reply message] 
upon which we will add the message to `pending messages` queue which will be processed once the partition
heals. This queue would be maintained at multiple nodes on both sides of partition.    
There is no need for a conflict resolution logic as all messages are distinct.

### How is it handling convergence for a message?
- Topology aware gossip
- Solve for message loss/network partition as described above

### Request-response

1. Broadcast
```json
{
  "type": "broadcast",
  "message": 1000
}
```

```json
{
  "type": "broadcast_ok"
}
```

2. Gossip
```json
{
  "type": "gossip",
  "message": 1000,
  "round": 10
}
```

```json
{
  "type": "gossip_ok",
  "in_reply_to": 34567
}
```

3. AreYouThere
```json
{
  "type": "areyouthere"
}
```

```json
{
  "type": "areyouthere_ok",
  "in_reply_to": 34567
}
```

### Topology aware candidate selection
Since its a simulated network, with all nodes equidistant we can come with simple formular for choosing 
candidates. We want to use a co-ordination free algo as topology of cluster never changes.
- Sort the list of N nodes at every node.
  import java.util.*;

public class GossipNodes {
private int N;  // Total number of nodes
private int logN;  // The number of nodes each node should communicate with

    public GossipNodes(int N) {
        this.N = N;
        this.logN = (int) Math.ceil(Math.log(N) / Math.log(2));
    }

    public List<Integer> getCommunicationNodes(int nodeID) {
        List<Integer> nodes = new ArrayList<>();
        for (int i = 1; i <= logN; i++) {
            nodes.add((nodeID + (int) Math.pow(2, i - 1)) % N);
        }
        return nodes;
    }
}

The idea is to have jumps start with size 1(so you always send to neighbour node) and increasing 
exponentially.

#### Optimizations
- Batch stuff when running in latency mode

#### Messages
Init
```
{"src": "c1", "dest": "n1", "body": {"type": "init", "node_id": "n1", "node_ids": ["n1", "n2", "n3"]}}
{"src": "c1", "dest": "n2", "body": {"type": "init", "node_id": "n1", "node_ids": ["n1", "n2", "n3"]}}
{"src": "c1", "dest": "n3", "body": {"type": "init", "node_id": "n1", "node_ids": ["n1", "n2", "n3"]}}
```

Broadcast
```
{"src": "c1", "dest": "n1", "body":{"type": "broadcast", "message": 1}}
{"src": "c1", "dest": "n1", "body":{"type": "broadcast", "message": 2}}
{"src": "c1", "dest": "n1", "body":{"type": "broadcast", "message": 3}}
{"src": "c1", "dest": "n1", "body":{"type": "broadcast", "message": 4}}
{"src": "c1", "dest": "n1", "body":{"type": "broadcast", "message": 5}}
```

Read
```
{"src": "c1", "dest": "n1", "body":{"type": "read"}}

```



