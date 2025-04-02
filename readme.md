# PODS-2025: Project-2-Phase-1

## Steps to Run the Services

### Way 1: Running Containers Using Docker

#### Prerequisites
Ensure Docker is installed and set up properly.

#### Download the Three Services
```
https://hub.docker.com/u/akashmaji
```
```bash
docker pull akashmaji/account-service-akka
docker pull akashmaji/marketplace-service-akka
docker pull akashmaji/wallet-service-akka
```

#### Run the Services
```bash
# Run "account-service"
docker run -p 8080:8080 --rm --name account-service \
                        --add-host=host.docker.internal:host-gateway \
                        akashmaji/account-service-akka:1.0 &

# Run "marketplace-service"
docker run -p 8081:8080 --rm --name marketplace-service \
                        --add-host=host.docker.internal:host-gateway \
                        akashmaji/marketplace-service-akka:1.0 &

# Run "wallet-service"
docker run -p 8082:8080 --rm --name wallet-service \
                        --add-host=host.docker.internal:host-gateway \
                        akashmaji/wallet-service-akka:1.0 &
```

#### Access the Services
The containers are being port-forwarded to:
- [http://localhost:8080/](http://localhost:8080/)
- [http://localhost:8081/](http://localhost:8081/)
- [http://localhost:8082/](http://localhost:8082/)

---

### Way 2: Running Containers Using Docker After Manual Building

#### Clone the Repository
```
https://github.com/surajmaji10/PODS-Project-2-Phase-1-WIP/tree/docker-
```

#### Run the Script for Containerized Execution
(Preferably run in three terminal windows, press `Ctrl+C` to kill each service)

```bash
# Start "account-service"
./docker.sh -1

# Start "marketplace-service"
./docker.sh -2

# Start "wallet-service"
./docker.sh -3
```

#### Cleanup
To clean up the running containers, use:
```bash
./clean.sh
```

#### Access the Services
The containers will be running locally at:
- [http://localhost:8080/](http://localhost:8080/)
- [http://localhost:8081/](http://localhost:8081/)
- [http://localhost:8082/](http://localhost:8082/)

---

## Running Test Cases
Run the test cases located in the `test-cases` folder:

```bash
python test_case_1.py
...
```

---

## Thanks
**Author:** Akash Maji  
**Email:** [akashmaji@iisc.ac.in](mailto:akashmaji@iisc.ac.in)

