# Makefile of the egrep clone.
#
#   make                  compile the sources and build the binary egrep.jar
#   make run REGEX="S(a|g|r)*on" FILE=testbeds/babylon.txt
#   make testbeds         download the Gutenberg books used by the tests
#   make test             correctness tests (needs the testbeds)
#   make compare          compare our output with grep -E on every testbed
#   make benchmark        performance tests, CSV files and charts in results/
#   make clean            remove everything that was built

JAVAC   = javac
JAVA    = java
JAR     = jar
OUT     = out
BIN     = egrep.jar
REGEX  ?= S(a|g|r)*on
FILE   ?= testbeds/babylon.txt

SOURCES = $(wildcard src/*.java)
TESTS   = $(wildcard test/*.java)

.PHONY: all build run testbeds test compare benchmark clean

all: $(BIN)

build: $(OUT)/Egrep.class

$(OUT)/Egrep.class: $(SOURCES)
	mkdir -p $(OUT)
	$(JAVAC) -encoding UTF-8 -d $(OUT) $(SOURCES)

$(BIN): $(OUT)/Egrep.class
	$(JAR) --create --file $(BIN) --main-class Egrep -C $(OUT) .

run: $(BIN)
	$(JAVA) -jar $(BIN) "$(REGEX)" $(FILE)

testbeds:
	bash scripts/download_testbeds.sh

test: $(SOURCES) $(TESTS)
	mkdir -p $(OUT)
	$(JAVAC) -encoding UTF-8 -d $(OUT) $(SOURCES) $(TESTS)
	$(JAVA) -cp $(OUT) CorrectnessTest

compare:
	bash scripts/compare_with_egrep.sh

benchmark:
	bash scripts/run_benchmarks.sh

clean:
	rm -rf $(OUT) $(BIN)
