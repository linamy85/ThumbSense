all: src/*.java
	mkdir -p bin
	javac -d bin src/*

run:
	java -cp bin Server 3000 $(F)

clear:
	rm -rf bin

