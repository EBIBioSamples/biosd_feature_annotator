cd $(dirname $0)
mydir=$(pwd)

cd "$mydir"

# All the stdouts from the LSF processes and other executions
find . -not -name 'purge.out' --exec rm -f \{\} \;

# The oldest logs, let's keep the most recent ones, just in case
find ./logs -name '*.log' -mtime +30 --exec rm -f \{\} \;
./annotate.sh --purge 7 --random-quota 10
