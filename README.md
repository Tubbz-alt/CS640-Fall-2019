# CS640: Computer Networks


| Name            | Description     | Code     | 
| -- | -- | -- |
| Sockets, Mininet & Performance | http://pages.cs.wisc.edu/~akella/CS640/F19/assignment1/ | 
| Link & Network Layer Forwarding  | http://pages.cs.wisc.edu/~akella/CS640/F19/assignment2/ |
| ARP, ICMP & RIP  | http://pages.cs.wisc.edu/~akella/CS640/F19/assignment3/ | 
| Software Defined Networking | http://pages.cs.wisc.edu/~akella/CS640/F19/assignment4/ |
| Flow Control & DNS    | http://pages.cs.wisc.edu/~akella/CS640/F19/assignment5/ | 


 
### Some useful commands

- Run mininet

    ```sh
    sudo ./run_mininet.py topos/single_sw.topo -a
    ```

- Start all iperf servers: 

    ```sh
    time
    h1 iperf -s &
    h2 iperf -s &
    h3 iperf -s &
    h4 iperf -s &
    h5 iperf -s &
    h6 iperf -s &
    h7 iperf -s &
    h8 iperf -s &
    h9 iperf -s &
    h10 iperf -s &

    ```

- Start a iperf client:

    ```sh
    time
    h1 iperf -c h4 -t 30 > throughput_h1-h4.txt & 
    h5 iperf -c h6 -t 30 > throughput_h5-h6.txt &

    ```

- Ping a host from another host:
    ```sh
    time
    h1 ping -c30 h4 > latency_h1_h4.txt &
    h5 ping -c30 h6 > latency_h5_h6.txt &
    
    ```

- Clean all tmp files and kill background processes:

    ```sh
    sudo mn -c
    sudo killall -9 java
    sudo killall -9 python
    ```

 - Start switch with route table and ARP cache
 
    ```sh
    java -jar VirtualNetwork.jar -v s1 &
    java -jar VirtualNetwork.jar -v s2 &
    java -jar VirtualNetwork.jar -v s3 &
    java -jar VirtualNetwork.jar -v s4 &
    java -jar VirtualNetwork.jar -v s5 &
    ```

 - Start router with route table

    ```sh
    java -jar VirtualNetwork.jar -v r1 -r rtable.r1 &
    java -jar VirtualNetwork.jar -v r2 -r rtable.r2 &
    java -jar VirtualNetwork.jar -v r3 -r rtable.r3 &
    java -jar VirtualNetwork.jar -v r4 -r rtable.r4 &
    java -jar VirtualNetwork.jar -v r5 -r rtable.r5 &
    ```
    
 - Start router with ARP cache

    ```sh
   	java -jar VirtualNetwork.jar -v r1 -a arp_cache &
    java -jar VirtualNetwork.jar -v r2 -a arp_cache &
    java -jar VirtualNetwork.jar -v r3 -a arp_cache &
    java -jar VirtualNetwork.jar -v r4 -a arp_cache &
    java -jar VirtualNetwork.jar -v r5 -a arp_cache &
    ```
    
- Start router
    
    ```sh
    java -jar VirtualNetwork.jar -v r1 &
    java -jar VirtualNetwork.jar -v r2 &
    java -jar VirtualNetwork.jar -v r3 &
    java -jar VirtualNetwork.jar -v r4 &
    java -jar VirtualNetwork.jar -v r5 &
    ```

- Dump the flow table

    ```sh
    sudo ovs-ofctl -O OpenFlow13 dump-flows s2
    ```
    
- Turn down a switch


    ```sh
    sudo ovs-vsctl del-br s1
    ```

- See which packets a host is sending/recieving

    ```sh
    tcpdump -v -n -i hN-eth0
    ```
