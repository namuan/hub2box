#!/usr/bin/bash

JOBS_DIR='jobs'
INPROGRESS_DIR='inprogress'
DONE_DIR='done'
CWD=`pwd`

for f in `ls $JOBS_DIR`;
do
	mv -v $JOBS_DIR/$f $INPROGRESS_DIR 
	ZIPNAME=$f".zip"
	echo "Processing "$f
	GITHUB_URL=`cat $INPROGRESS_DIR/$f`
	# TEST if the directory exists
	if [ ! -d $f ]; then
		# clone github repo
		git clone $GITHUB_URL $f
	fi

	if [ ! -f $f/$ZIPNAME ]; then
		# TEST if the file exists
		# create zip file
		cd $f
		git archive HEAD --format=zip --output=$ZIPNAME -v
		cd $CWD
	fi

	# use dropbox uploader to upload file
	./dropbox_uploader.sh upload $f/$ZIPNAME $ZIPNAME

	# delete folder
	rm -vrf $f

	# move file to DONE_DIR
	mv -v $INPROGRESS_DIR/$f $DONE_DIR
done