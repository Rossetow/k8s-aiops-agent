.PHONY: build test clean docker-build docker-push

IMAGE ?= rossetow/kubernetes-aiops-agent:latest

CONTEXT ?= $(shell kubectl config current-context)

build:
	mvn clean package

test:
	mvn test

clean:
	mvn clean

docker-build:
	docker build -t $(IMAGE) -f src/main/docker/Dockerfile.jvm .

docker-push:
	docker push $(IMAGE)

