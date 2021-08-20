#!/bin/bash
for i in 1 2 3 4 5
do
python run.py | grep "Total time"
done
exit 0