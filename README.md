# CS640
 
# Some useful commands
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