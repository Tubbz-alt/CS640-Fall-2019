# CS640
 
# Some useful commands

- To run mininet

    ```sh
    sudo ./run_mininet.py topos/single_sw.topo -a
    ```

- To start all iperf servers: 

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

- To start a iperf client:

    ```sh
    time
    h1 iperf -c h4 -t 30 > throughput_h1-h4.txt & 
    h5 iperf -c h6 -t 30 > throughput_h5-h6.txt &

    ```

- To ping a host from another host:
    ```sh
    time
    h1 ping -c30 h4 > latency_h1_h4.txt &
    h5 ping -c30 h6 > latency_h5_h6.txt &
    
    ```

- To clean all tmp files:

    ```sh
    sudo mn -c
    ```

 - To start switch with route table and ARP cache
 
    ```sh
    java -jar VirtualNetwork.jar -v s1 &
    java -jar VirtualNetwork.jar -v s2 &
    java -jar VirtualNetwork.jar -v s3 &
    java -jar VirtualNetwork.jar -v s4 &
    java -jar VirtualNetwork.jar -v s5 &
    ```

 - To start router with route table

    ```sh
    java -jar VirtualNetwork.jar -v r1 -r rtable.r1 &
    java -jar VirtualNetwork.jar -v r2 -r rtable.r2 &
    java -jar VirtualNetwork.jar -v r3 -r rtable.r3 &
    java -jar VirtualNetwork.jar -v r4 -r rtable.r4 &
    java -jar VirtualNetwork.jar -v r5 -r rtable.r5 &
    ```
    
 - To start router with ARP cache

    ```sh
   	java -jar VirtualNetwork.jar -v r1 -a arp_cache &
    java -jar VirtualNetwork.jar -v r2 -a arp_cache &
    java -jar VirtualNetwork.jar -v r3 -a arp_cache &
    java -jar VirtualNetwork.jar -v r4 -a arp_cache &
    java -jar VirtualNetwork.jar -v r5 -a arp_cache &
    ```
    
- To start router
    
    ```sh
    java -jar VirtualNetwork.jar -v r1 &
    java -jar VirtualNetwork.jar -v r2 &
    java -jar VirtualNetwork.jar -v r3 &
    java -jar VirtualNetwork.jar -v r4 &
    java -jar VirtualNetwork.jar -v r5 &
    ```

- To dump the flow table

    ```sh
    sudo ovs-ofctl -O OpenFlow13 dump-flows s2
    ```
    
- To turn down a switch


    ```sh
    sudo ovs-vsctl del-br s1
    ```
