

root := justfile_directory()
tester-path := root / '..' / 'redis-tester'
test-def := root / 'local' / 'tests.json'

build:
    mvn -B package

test: build
    cd {{tester-path}}; make build

    CODECRAFTERS_SUBMISSION_DIR={{root}} \
    CODECRAFTERS_TEST_CASES_JSON=`cat {{test-def}}` \
    {{tester-path}}/dist/main.out


lint:
    detekt --plugins detekt-formatting --auto-correct --build-upon-default-config
