#!/bin/sh

# 
# Invokes multiple instances (ie, JVMs) of the command annotate.sh, using an LSF-based cluster (tested at the EBI),
# against all the properties in the BioSD database. This first computes the number of experimental properties
# in the BioSD database and then it split them in chunks to be passed to multiple processes.
# 

# The LSF group used for loading jobs
if [ "$LSF_GROUP" == '' ]; then LSF_GROUP='biosd_annotator'; fi

# The number of running nodes at any time (i.e., the LSF -L option for LSF_GROUP)
if [ "$LSF_NODES" == '' ]; then LSF_NODES=5; fi

# How many sample property values are annotated by each LSF job (i.e., instance of annotate.sh)?
# We will create as many jobs as necessary, depending on annotate.sh --property-count
if [ "$PROPERTIES_PER_JOB" == '' ]; then PROPERTIES_PER_JOB=1000000; fi


# --- end of invoker-passed properties. 

cd "$(dirname $0)"
MYDIR="$(pwd)"


# If it doesn't already exist, create an LSF group to manage a limited running pool
if [ "$(bjgroup -s /$LSF_GROUP 2>&1)" == 'No job group found' ]; then
  bgadd -L $LSF_NODES /$LSF_GROUP
else
  bgmod -L $LSF_NODES /$LSF_GROUP # Just to be sure
fi

# This is absolutely necessary when you run the annotator through the cluster, since every thread in every JVM instance 
# takes one DB connection and, without this control, parallel instances will soon overcome the server limit.
#
nthreads=$(( 120 / $LSF_NODES ))
export OPTS="$OPTS -Duk.ac.ebi.fg.biosd.annotator.maxThreads=$nthreads" 


# How many properties do we have?
# 
pval_size=$(./annotate.sh --property-count 2>/dev/null)

if [[ ! ( $pval_size -gt 0 ) ]]
then
	echo
	echo 'No property values to annotate, exiting'
	echo
	echo
  exit 1
fi

# Remove locks. Be aware that this is not compatible with annotator instances running in parallel
./annotate.sh --unlock

# Split the whole job into chunks
# 
echo "Processing $pval_size property values with $LSF_NODES nodes, $PROPERTIES_PER_JOB records per job"
echo "Using additional command line arguments:" ${1+"$@"}

chunkct=1
for (( offset=0; offset<$pval_size; offset+=$PROPERTIES_PER_JOB ))
do
	bsub -J biosdann$chunkct -g /$LSF_GROUP -oo "./logs/biosdann_$chunkct".out -M 15000 \
		./annotate.sh --offset $offset --limit $PROPERTIES_PER_JOB ${1+"$@"}
	(( chunkct++ ))
done

# Now poll the LSF and wait until all the jobs terminate.
#
echo 'All the exporting jobs submitted, now waiting for their termination, please be patient.'
while [ "$(bjobs -g /$LSF_GROUP 2>&1)" != 'No unfinished job found' ]
do
  sleep 5m
done

echo
echo 'All Finished.'
echo
echo
