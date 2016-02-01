cd $(dirname $0)
mydir=$(pwd)

cd "$mydir"
find ./logs/* -mtime +60 --exec rm -f \{\} \;
./annotate.sh --purge 7 --random-quota 10
