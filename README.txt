To start the services first call ./runme.sh -c to compile everything into the compiled directory(it will make the directory and the related database)
Next, call ./runme.sh -u, ./runme.sh -o, ./runme.sh -p in three terminals to run each service on a port.
Open a new terminal, call ./runme.sh -w "workload_file.txt" to use the workload parser and send requests to the services. Make sure that the workloadfile is in the same directory with runme.sh
