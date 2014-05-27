set -x
set -e
srid=$(lein run -m system.mturk.response-converter data/responses/wage_survey/HITResultsFor2GZHRRLFQ0OT6OX3OP8OKEGRDBJHQ4.csv data/samples/wage_survey.csv 0)
srid=$(lein run -m system.mturk.response-converter data/responses/wage_survey/HITResultsFor3N7PQ0KLI51X8QZZ5Y3V9BR4UH13EK.csv data/samples/wage_survey.csv $srid)
lein run -m system.mturk.response-converter data/responses/wage_survey/HITResultsFor3E9ZFLPWOY4L4T8ZL60A16E0T3MIXH.csv data/samples/wage_survey.csv $srid
