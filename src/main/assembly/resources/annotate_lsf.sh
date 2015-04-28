#!/bin/sh

# 
# Invokes multiple instances (ie, JVMs) of the command annotate.sh, using an LSF-based cluster (tested at the EBI),
# against all the properties in the BioSD database. This first computes the number of experimental proeprties
# in the BioSD database and then it split them in chunks to be passed to multiple processes.
# 
# Note that there is no process parallelism (only multi-thread parallelism), since updating multiple instances 
# of the same annotation values in parallel is doomed to fail. LSF is used only to get the best nodes to
# perform computations one at a time.
# 

# The LSF group used for loading jobs
if [ "$LSF_GROUP" == '' ]; then LSF_GROUP='biosd_annotator'; fi


# --- end of invoker-passed properties. 

cd "$(dirname $0)"
MYDIR="$(pwd)"


# If it doesn't already exist, create an LSF group to manage a limited running pool
if [ "$(bjgroup -s /$LSF_GROUP 2>&1)" == 'No job group found' ]; then
  bgadd -L 1 /$LSF_GROUP
else
  bgmod -L 1 /$LSF_GROUP # Just to be sure
fi

# Split the whole job into chunks
# 
echo "Invoking the LSF with the parameters: " ${1+"$@"} ". Please be patient..."

bsub -K -J biosdann -g /$LSF_GROUP -oo "./logs/biosdann_lsf".out -M 38000 \
	./annotate.sh ${1+"$@"}

echo
echo 'The end!'
echo
echo
