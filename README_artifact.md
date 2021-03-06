# SURVEYMAN: Programming and Automatically Debugging Surveys

## Artifact Evaluation

[AE Guidelines](http://2014.splashcon.org/track/splash2014-artifacts) state that artifact submissions consist of three components:

1. Overview of the artifact
2. URL pointing to a single file containing the artifact
3. MD5 hash of #2.

This document satisfies criterion #1. Criterion #2 will be satisfied with a release called `aec-final`, which will have the zipped file containing a virtual machine image with SurveyMan installed on it. The code and the readme version of this document will be available on the [`artifact-evaluation` branch of the SurveyMan repository](https://github.com/etosch/SurveyMan/tree/artifact-evaluation). We intend to supply a VM with both the code and the compiled artifact. We are currently using VirtualBox 4.3.12. The VM name, username, and password are all `paper42`. Unfortunately, both authors have been having issues uploading large files, so we may only be able to offer the distributed jar until after 14 June 2014.

## Overview of the Artifact

### Getting Started
The VM will have two relevant folders on its desktop : SurveyMan and surveyman-1.5. The former contains the source code for this project. The latter is the unzipped folder of the code and documents we distribute. The remainder of these instructions assume that the AEC member has chosen to download the release listed at the link provided in HotCrp.

 SurveyMan has been tested on OracleJDK 7, OpenJDK 7, and OpenJDK 6. Only setup requires Python and should work on both Python 2.7 and Python 3.

1. Download the release `aec-final.` (A link will appear here when the release is ready)
2. Unzip the folder in a convenient location. The folder should contain the following files:
    1. setup.py
    2. params.properties
    3. custom.js
    4. custom.css
    5. data (directory)
3. Run setup.py. This will create the folder called surveyman in your home directory and will copy params.properties, custom.js, and custom.css into that folder.
4. If you would like to test the Amazon Mechanical Turk backend, you will need to have an account with Amazon Web Services. If you do not already have an account, you can sign up [here](http://aws.amazon.com/). Please note that this may require 24 hours to activate. You will need a valid credit card and a phone that recieves text messages. You will also need to register as a Mechanical Turk Requester and a Mechanical Turk Worker. The default settings post to the Mechanical Turk "sandbox," so you will not need to spend any money in order to test this software.

### Step-by-Step Instructions for Evaluation
Open a terminal to the location of your `surveyman-1.5` folder. SurveyMan can currently run with two backends: a local version, and Amazon's Mechanical Turk. There will be a `data` folder, which will contain sample surveys and sample data. There will also be a `src` folder, which contains the javascript necessary to run the edu.umass.cs.surveyman.survey locally.

#### Evaluation Goal 1 : Test a edu.umass.cs.surveyman.survey using both backends.

__LOCALHOST__
Navigate to your `surveyman-1.5` folder in a terminal and execute `java -jar surveyman-x.y-standalone.jar` to see the usage. You can run a test edu.umass.cs.surveyman.survey, such as the prototypicality edu.umass.cs.surveyman.survey featured in the paper, with the command `java -jar surveyman-x.y-standalone.jar data/samples/prototypicality.csv --backend_type=LOCALHOST`. The URL will be printed to the command line. __It may take a minute for the URL to be printed.__  You can take the edu.umass.cs.surveyman.survey yourself by copying and pasting it into a browser of your choice; surveyman has been tested on Firefox 29.0.1 and Chrome 34.0.1847.137.

The default setting is to stop the edu.umass.cs.surveyman.survey when it has acquired at least one valid response. Since we need a larger number of responses to distinguish valid responses from bad ones, the system will start by classifying the results as valid. After responding to the edu.umass.cs.surveyman.survey, you can try running it again. When the system registers that sufficient responses have been collected, the edu.umass.cs.surveyman.survey will close.

We have primarily used the local server for testing purposes. A production backend that might be used in a classroom setting would need a way of tracking users. We have not yet implemented this, since we have not yet been asked to do so by our users.

__MTURK__
In order to test on Mechanical Turk, you will need to grab some credentials. Navigate [here](https://console.aws.amazon.com/iam/home?#security_credential) and select "Access Keys (Access ID and Secret Key Access)". Click on "Create new access key" and select "Download key file." You can run surveyman with credentials in one of two ways. The recommended way is to save the access key file as `mturk_config` in your surveyman home folder (i.e. `~/surveyman` -- where all those files were copied to). If you're tired and accidentally just clicked all the default settings, a file named `rootkey.csv` will be downloaded to your default location. You can then supply this file as an argument to the jar. 

As before, when you begin running the edu.umass.cs.surveyman.survey, the URL will be spit out on the console. You can navigate here and take the edu.umass.cs.surveyman.survey. The default setting in `params.properties` is to post to the AMT sandbox. In the event that the program decides that your response isn't enough, send the


Surveys are randomized according to how they are specified in their source files. However, the order in which questions and options appear is determined by a RNG seeded with the assignment id. For AMT, if you navigate away from the page, you still have a lock on the job (or "Human Intelligence Task", as they're known). The questions will appear in the same order in which they were originally presented. 

__Running surveys featured in the paper__
The three surveys we featured in the paper can be found in the data folder:

1. `data/samples/phonology.csv`
2. `data/samples/prototypicality.csv`
3. `data/samples/wage_survey.csv`

The phonology edu.umass.cs.surveyman.survey illustrates a completely flat edu.umass.cs.surveyman.survey of Likert-like questions. This is an example of a very simple edu.umass.cs.surveyman.survey.

The prototypicality edu.umass.cs.surveyman.survey illustrates how question variants are written. One question from each block is selected. Since these questions aren't truly branching questions -- the user should proceed to whichever randomized block comes next -- the branch destination is set to NEXT. This defers computation of the next question until runtime.

The wage edu.umass.cs.surveyman.survey illustrates how we test randomization as part of the edu.umass.cs.surveyman.survey -- it contains a branch question that routes the user down one of two paths. One path is a single block, whose contents can be fully randomized. It contains one "branch question," which will route the user to the final thank you "question." The other path is a block that contains subblocks for each of the questions. This induces a total order on the question. The final question is a "branch" question that sends the respondent to the final thank you "question."

__Difference in observed randomization between running locally and running on AMT__
We seed the random number generator with the assignment id.  The purpose of this is to ensure that an individual respondent on AMT will see the same order of questions if they navigate away from the HIT and later return to it.

The local server we have provided does not maintain accounts; as a result, each time the user/tester/AEC member visits a page, a new assignment id is issued. The person testing the edu.umass.cs.surveyman.survey will observe different version of the edu.umass.cs.surveyman.survey upon refreshing the page.

#### Evaluation Goal 2 : Reproduce results reported in the paper

The static and dynamic analyses described in the paper can be reproduced by running the Report program. The static analyses print out path information, maximum entropy, and a suggested payment to valid respondents for completion of the entire edu.umass.cs.surveyman.survey. The payment is currently computed from the average path length, an estimated time to answer one question of 10s and the federal minimum wage of $7.25.

The dynamic analysis first identifies suspected bad actors, and removes them from the subsequent analyses. Then it tests for pairwise correlations between questions, and checks whether this correlation was specified as expected by the user. It prints out unexpected correlations and failed expected correlations (i.e. those that have a coefficient below some threshold. Then it reports any order biases and variant biases before printing out suggested bonus payments.

We have identified some typographical errors in the originally submitted paper and have refined some of our calculations since that submission. Consequently, we have included the most current pdf with the artifact. 

We have included both the raw Mechanical Turk results and the results format produced by SurveyMan for all three case studies. You can test each by running the following commands. __The dynamic analyses will take longer to run than the static analyses. You can reduce this time by setting the `--classifier` flag to `all`.__ The output of each program is printed to standard out.

__Case Study 1: Phonology (Section 6.1)__

`java -cp surveyman-1.5-standalone.jar Report --report=static data/samples/phonology.csv`

`java -cp surveyman-1.5-standalone.jar Report --report=dynamic --results=data/results/phonology_results.csv data/samples/phonology.csv`

The above commands run over the entirety of the phonology surveys. The phonology edu.umass.cs.surveyman.survey was run four times. The first run was early in the development of this software and contained little information. We did not use these tools on that data, so we have not included it. The remaining three experiments are included. Since our The datasets over which the analyses in the paper were performed are `english_phonology_results.csv`, `english_phonology2_results.csv`, and `english_phonology3_results.csv`. They are all combined in `english_phonology_all.csv`.

The phonology edu.umass.cs.surveyman.survey is annotated with expected correlations; these correlations were added by one of the authors of the paper (not a linguist) to test the system. This edu.umass.cs.surveyman.survey was performed primarily to investigate the properties of random and lazy respondents.

The committed member will note that close to 50% of the respondents are classified as bad actors. This is significantly higher than what is reported in the paper. The percentage bots reported in the submitted paper were calcuated from an old version of our quality control system. This older version looked for positional preferences in responses and only detected 3 bad actors. The quality control mechanism reported in the paper is the one currently implemented in this distribution of the artifact. We believe that the new version more accurately represents the true classification of the data and have verified a subset of the phonology data through manual annotation of the gold standard heuristics provided to us by our colleatures. We will report a full analysis of these results our camera-ready version.

__Case Study 2: Psycholinguistics (Section 6.2)__

`java -cp surveyman-1.5-standalone.jar Report --report=static data/samples/prototypicality.csv`

`java -cp surveyman-1.5-standalone.jar Report --report=dynamic --results=data/results/prototypicality_results.csv data/samples/prototypicality.csv`

This edu.umass.cs.surveyman.survey illustrated the effects of changing wording in a edu.umass.cs.surveyman.survey. Our collaborators provided us with a edu.umass.cs.surveyman.survey that gives variants on both question and option wording. If you run this edu.umass.cs.surveyman.survey with the `--classifier=all` flag, you can see problematic variants for 463 detected. At the default setting of `--alpha=0.05`, order biases typically appear. If you set `--alpha=0.01` and use `--classifier=entropy-norm`, both order biases and wording biases typically disappear.

Correlations are tagged by their prototypicality and parity.

__Case Study 3: Labor Economics (Section 6.3)__

`java -cp surveyman-1.5-standalone.jar Report --report=static data/samples/wage_survey.csv`

`java -cp surveyman-1.5-standalone.jar Report --report=dynamic --results=data/results/wage_survey_results.csv data/samples/wage_survey.csv`

The wage edu.umass.cs.surveyman.survey uses more data than what's reported in the paper. This edu.umass.cs.surveyman.survey had a high degree of breakoff.

Note that the system's inability to find correlations between identical questions is due to the low number of responses for those questions; we return correlations of 0 when we have 5 or fewer data points.

__Comments on Report Output__
Our goal with the report output is to produce something human-readable. We have a "raw" results csv that is produced by running the system, fetching responses from the backend, and unifying those encoded results with the internal representation of the edu.umass.cs.surveyman.survey.

Our end-users have varying skills and needs. Those who which to repeat analyses in R and run their own, domain-specific analyses can use the raw output of the system and validate against the report. Those who are less intersted in such data analysis tools can use the report directly. We are currently working to improve the experience for non-programmers by providing them with an interative interface for exploring the statistical analyses we produce.

### Remarks on differences from the paper
The system itself should be consistent with what is described in the paper. AEC members might try altering the edu.umass.cs.surveyman.survey source, or designing their own edu.umass.cs.surveyman.survey code. We are including the source code on the VM, should the AEC members decide to play around with it.

Some of our analyses in the paper differ from those found in the report. We enumerate them here:

| Section | Metric | Value in Paper | Value in Report | Explanation |
| --- | --- | --- | --- | --- |
| 6.1 | Max. Entropy | 195.32 | 195.32 | |
| 6.1 | Min., Max., Avg. Path lengths | 99 | 99 | |
| 6.1 | Bad Actors | 6%, 15% | 40%-50% | The paper used an older version of quality control that only modeled positional preferences and not inattentive respondents. |
| 6.2 | Max. Entropy | 34 | 34 | |
| 6.2 | Max., Min., Avg. Path lengths | 17 | 17 | |
| 6.2 | Variant Biases | 3 pairs for 463, one for 158, one of 2 | none | The more aggressive quality control removes responses that cause these biases; if we run the current system without the respondent QC (i.e. with `--classifier=all`), we see two inequivalent pairs for 463 and one for 158. | 
| 6.3 | Total respondents | 69 | 132 | Continued running the edu.umass.cs.surveyman.survey after the paper was submitted |
| 6.3 | Max. Entropy | 342 | 80.45 | Two things : (1) We lowered our bound on our entropy calculation and (2) Fatigue? Seriously, this must have been from another experiment or maybe my current bank account balance becuase I could not reconstruct how I came up with this number. |
| 6.3 | Min., Max., Avg. Path | 40 | 41 | Maybe an older version of the edu.umass.cs.surveyman.survey? |
| 6.3 | Breakoff Questions, concentration | Early on, demographic questions | ditto | | 


Although we have not been able to include it in time for the artifact evaluation, we intend to update the paper with a more thorough analysis of our quality control mechanisms. The original quality control code was written in Python. Our current version is written in Clojure. We would also like to note that there are some other small implementation changes between the code we provided for the paper and now : for example, we unify the responses over variants for order biases. That is, questions drawn from an ALL block will be treated as a single question. This allows us to have sufficient data points to address order bias when we have question variants.




The source code can be found at http://github.com/etosch/SurveyMan. 
