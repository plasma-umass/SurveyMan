package edu.umass.cs.surveyman.qc;

import edu.umass.cs.surveyman.SurveyMan;
import edu.umass.cs.surveyman.analyses.IQuestionResponse;
import edu.umass.cs.surveyman.analyses.ISurveyResponse;
import edu.umass.cs.surveyman.analyses.KnownValidityStatus;
import edu.umass.cs.surveyman.analyses.OptTuple;
import edu.umass.cs.surveyman.survey.Block;
import edu.umass.cs.surveyman.survey.Component;
import edu.umass.cs.surveyman.survey.Question;
import edu.umass.cs.surveyman.survey.Survey;
import edu.umass.cs.surveyman.survey.exceptions.SurveyException;

import java.io.Reader;
import java.util.*;

public class QCMetrics {

    private static double log2(double p) {
        return Math.log(p) / Math.log(2.0);
    }

    /**
     * Takes in a list of Blocks; returns a list of lists of Blocks representing all possible paths through the survey.
     * See @etosch's blog post for more detail.
     * @param blockList A list of blocks we would like to traverse.
     * @return A list of lists of blocks, giving all possible traversals through the original input.
     */
    public static List<List<Block>> getDag(List<Block> blockList) {
        Collections.sort(blockList);
        if (blockList.isEmpty()) {
            // return a singleton list of the empty list
            List<List<Block>> newSingletonList = new ArrayList<List<Block>>();
            newSingletonList.add(new ArrayList<Block>());
            return newSingletonList;
        } else {
            Block thisBlock = blockList.get(0);
            if (thisBlock.hasBranchQuestion()) {
                Set<Block> dests = thisBlock.getBranchDestinations();
                List<List<Block>> blists = new ArrayList<List<Block>>();
                for (Block b : dests) {
                    // for each destination, find the sublist of the blocklist starting with the destination
                    int index = blockList.indexOf(b);
                    if (index > -1) {
                        List<List<Block>> dags = getDag(blockList.subList(index, blockList.size()));
                        for (List<Block> dag : dags) {
                            dag.add(thisBlock);
                        }
                        blists.addAll(dags);
                    }
                }
                return blists;
            } else {
                List<List<Block>> subDag = getDag(blockList.subList(1, blockList.size()));
                for (List<Block> blist : subDag) {
                    blist.add(thisBlock);
                }
                return subDag;
            }
        }
    }

    /**
     * Returns paths through **blocks** in the survey. Top level randomized blocks are all listed last
     * @param s The survey whose paths we want to enumerate
     * @return A List of all paths through the survey. A path is represented by a List. There may be duplicate paths,
     * so if you need distinct paths, you will need to filter for uniqueness.
     */
    protected static List<List<Block>> getPaths(Survey s) {
        List<List<Block>> retval = new ArrayList<List<Block>>();
        Map<Boolean, List<Block>> partitionedBlocks = Interpreter.partitionBlocks(s);
        List<Block> topLevelRandomizableBlocks = partitionedBlocks.get(true);
        List<Block> nonrandomizableBlocks = partitionedBlocks.get(false);
        Collections.sort(nonrandomizableBlocks);
        List<List<Block>> dag = getDag(nonrandomizableBlocks);
        SurveyMan.LOGGER.info("Computing paths for survey having DAG with "+dag.size()+" paths through fixed blocks.");
        for (List<Block> blist : dag) {
            if (blist.isEmpty())
                continue;
            blist.addAll(topLevelRandomizableBlocks);
            retval.add(blist);
        }
        return retval;
    }

    /**
     * Returns the set of enclosing blocks for this survey response.
     * @param r A single survey responses
     * @return The blocks the respondent has traversed in order to produce this response.
     */
    private static Set<Block> getPath(ISurveyResponse r) {
        Set<Block> retval = new HashSet<Block>();
        for (IQuestionResponse questionResponse : r.getResponses()) {
            retval.add(questionResponse.getQuestion().block);
        }
        return retval;
    }

    /**
     * Returns the counts for each path; see @etosch's blog post on the calculation.
     * @param paths The list of list of blocks through the survey; can be obtained with getPaths or getDag
     * @param responses The list of actual or simulated responses to the survey
     * @return A map from path to the frequency the path is observed.
     */
    private static Map<List<Block>, List<ISurveyResponse>> makeFrequenciesForPaths(List<List<Block>> paths,
                                                                     List<ISurveyResponse> responses) {
        Map<List<Block>, List<ISurveyResponse>> retval = new HashMap<List<Block>, List<ISurveyResponse>>();
        for (ISurveyResponse r : responses) {
            for (List<Block> path : paths) {
                Set<Block> pathTraversed = getPath(r);
                if (path.containsAll(pathTraversed)){
                    if (retval.containsKey(path))
                        retval.get(path).add(r);
                    else {
                        List<ISurveyResponse> srlist = new ArrayList<ISurveyResponse>();
                        srlist.add(r);
                        retval.put(path, srlist);
                    }
                }
            }
        }
        return retval;
    }

    private static List<Question> removeFreetext(List<Question> questionList) {
        List<Question> questions = new ArrayList<Question>();
        for (Question q : questionList) {
            if (!q.freetext)
                questions.add(q);
        }
        return questions;
    }


    /**
     * Returns equivalent answer options (a list of survey.Component)
     * @param q The question whose variants we want. If there are no variants, then a set of just this question is
     *          returned.
     * @param c The answer the respondent provided for this question.
     * @return
     */
    public static List<Component> getEquivalentAnswerVariants(Question q, Component c) {
        List<Component> retval = new ArrayList<Component>();
        List<Question> variants = q.getVariants();
        int offset = q.getSourceRow() - c.getSourceRow();
        for (Question variant : variants) {
            for (Component thisC : variant.options.values()) {
                int thisOffset = variant.getSourceRow() - thisC.getSourceRow();
                if (thisOffset == offset)
                    retval.add(thisC);
            }
        }
        return retval;
    }

    public static double surveyEntropy(Survey s, List<ISurveyResponse> responses){
        List<List<Block>> paths = getPaths(s);
        Map<List<Block>, List<ISurveyResponse>> pathMap = makeFrequenciesForPaths(paths, responses);
        int totalResponses = responses.size();
        double retval = 0.0;
        for (Question q : removeFreetext(s.questions)) {
            for (Component c : q.options.values()) {
                for (List<Block> path : paths) {
                    List<Component> variants = getEquivalentAnswerVariants(q, c);
                    List<ISurveyResponse> responsesThisPath = pathMap.get(path);
                    List<ISurveyResponse> ansThisPath = new ArrayList<ISurveyResponse> ();
                    for (ISurveyResponse r : responsesThisPath) {
                        if (r.surveyResponseContainsAnswer(variants)) {
                            ansThisPath.add(r);
                        }
                    }
                    double p = ansThisPath.size() / (double) totalResponses;
                    retval += log2(p) * p;
                }
            }
        }
        return -retval;
    }

    /**
     * Returns all questions in a block list (typically the topLevelBlocks of a Survey).
     * @param blockList
     * @return
     */
    public static List<Question> getQuestions(final List<Block> blockList) {
        List<Question> questions = new ArrayList<Question>();
        for (Block block : blockList) {
            if (block.branchParadigm != Block.BranchParadigm.ALL)
                questions.addAll(block.questions);
            else {
                questions.add(block.questions.get(new Random().nextInt(block.questions.size())));
            }
            questions.addAll(getQuestions(block.subBlocks));
        }
        return questions;
    }

    /**
     * Returns the maximum possible entropy for a single Question.
     * @param question
     * @return
     */
    private static double maxEntropyOneQuestion(Question question) {
        double retval = 0.0;
        int numOptions = question.options.size();
        if (numOptions != 0) {
            retval += log2((double) numOptions);
        }
        return retval;
    }


    /**
     * Returns the total entropy for a list of Questions.
     * @param questionList
     * @return
     */
    private static double maxEntropyQlist(List<Question> questionList) {
        double retval = 0.0;
        for (Question q : questionList) {
            retval += maxEntropyOneQuestion(q);
        }
        return retval;
    }


    /**
     * Returns the path with the highest entropy.
     * @param blists
     * @return
     */
    private static List<Block> getMaxPathForEntropy(List<List<Block>> blists) {
        List<Block> retval = new ArrayList<Block>();
        double maxEnt = 0.0;
        for (List<Block> blist : blists) {
            double ent = maxEntropyQlist(getQuestions(blist));
            if (ent > maxEnt) {
                maxEnt = ent;
                retval = blist;
            }
        }
        return retval;
    }

    public static double getMaxPossibleEntropy(Survey s) {
        return maxEntropyQlist(getQuestions(getMaxPathForEntropy(getPaths(s))));
    }

    public static int minimumPathLength(Survey survey){
        List<List<Block>> paths = getPaths(survey);
        int min = Integer.MAX_VALUE;
        for (List<Block> path : paths) {
            int pathLength = getQuestions(path).size();
            if (pathLength < min)
                min = pathLength;
        }
        return min;
    }

    public static int maximumPathLength(Survey survey) {
        List<List<Block>> paths = getPaths(survey);
        int max = Integer.MIN_VALUE;
        for (List<Block> path : paths) {
            int pathLength = getQuestions(path).size();
            if (pathLength > max) {
                max = pathLength;
            }
        }
        return max;

    }

    public static double averagePathLength(Survey survey) throws SurveyException {
        int n = 5000;
        int stuff = 0;
        for (int i = 0 ; i < n ; i++) {
            RandomRespondent rr = new RandomRespondent(survey, RandomRespondent.AdversaryType.UNIFORM);
            stuff += rr.getResponse().getResponses().size();
        }
        return (double) stuff / n;
    }

    /**
     * When used without the survey argument, this returns frequencies that do not calculate smoothing.
     * @param responses The list of actual or simulated responses to the survey
     * @return A map from question ids to maps of option ids to counts.
     */
    public static Map<String, Map<String, Integer>> makeFrequencies(List<ISurveyResponse> responses) {
        return makeFrequencies(responses, null);
    }

    /**
     * Creates a frequency map for the actual responses to the survey. If the survey argument is not null, it will c
     * calculate LaPlace smoothing.
     * @param responses The list of actual or simulated responses to the survey.
     * @param survey The survey these respondents answered.
     * @return A map from question ids to a map of option ids to counts.
     */
    public static Map<String, Map<String, Integer>> makeFrequencies(List<ISurveyResponse> responses, Survey survey) {
        Map<String, Map<String, Integer>> retval = new HashMap<String, Map<String, Integer>>();
        Set<String> allComponentIdsSelected = new HashSet<String>();
        for (ISurveyResponse sr : responses) {
            for (IQuestionResponse qr : sr.getResponses()) {
                String quid = qr.getQuestion().quid;
                Map<String, Integer> tmp = new HashMap<String, Integer>();
                if (retval.containsKey(quid)) {
                    tmp = retval.get(quid);
                } else {
                    retval.put(quid, tmp);
                }
                List<String> cids = OptTuple.getCids(qr.getOpts());
                for (String cid : cids) {
                    allComponentIdsSelected.add(cid);
                    if (tmp.containsKey(cid))
                        tmp.put(cid, tmp.get(cid) + 1);
                    else tmp.put(cid, 1);
                }
            }
        }
        // LaPlace (+1 smoothing)
        if (survey != null) {
            int numberNeedingSmoothing = 0;
            for (Question q : survey.questions) {
                for (Component c : q.options.values()) {
                    if (!retval.containsKey(q.quid)) {
                        retval.put(q.quid, new HashMap<String, Integer>());
                    }
                    retval.get(q.quid).put(c.getCid(), 1);
                    if (!allComponentIdsSelected.contains(c.getCid())) {
                        numberNeedingSmoothing++;
                    }
                }
            }
            if (numberNeedingSmoothing > 0)
                SurveyMan.LOGGER.info("Number needing smoothing " + numberNeedingSmoothing);
        }
        return retval;
    }

    public static Map<String, Map<String, Double>> makeProbabilities(Map<String, Map<String, Integer>> frequencies) {
        Map<String, Map<String, Double>> retval = new HashMap<String, Map<String, Double>>();
        for (Map.Entry<String, Map<String, Integer>> e : frequencies.entrySet()) {
            String quid = e.getKey();
            Map<String, Integer> map = e.getValue();
            double total = 0.0;
            for (Integer i : map.values()) {
                total += i;
            }
            retval.put(quid, new HashMap<String, Double>());
            for (String cid : map.keySet()) {
                retval.get(quid).put(cid, map.get(cid) / total);
            }
        }
        return retval;
    }

    public static double getLLForResponse(ISurveyResponse surveyResponse, Map<String, Map<String, Double>> probabilities) {
        double ll = 0.0;
        for (IQuestionResponse questionResponse : surveyResponse.getResponses()) {
            String qid = questionResponse.getQuestion().quid;
            for (String cid : OptTuple.getCids(questionResponse.getOpts())) {
                ll += log2(probabilities.get(qid).get(cid));
            }
        }
        return ll;
    }

    public static double getEntropyForResponse(ISurveyResponse surveyResponse, Map<String, Map<String, Double>> probabilities) {
        double ent = 0.0;
        for (IQuestionResponse questionResponse : surveyResponse.getResponses()) {
            String qid = questionResponse.getQuestion().quid;
            for (String cid : OptTuple.getCids(questionResponse.getOpts())) {
                double p = probabilities.get(qid).get(cid);
                assert p > 0.0;
                ent += p * log2(p);
            }
        }
        return -ent;
    }

    public static List<Double> calculateLogLikelihoods(List<ISurveyResponse> responses, Map<String, Map<String, Double>> probabilities) {
        List<Double> retval = new LinkedList<Double>();
        for (ISurveyResponse sr : responses) {
            retval.add(getLLForResponse(sr, probabilities));
        }
        return retval;
    }

    public static List<String> getQuestionIds(List<IQuestionResponse> questionResponses) {
        List<String> quids = new ArrayList<String>();
        for (IQuestionResponse qr : questionResponses) {
            quids.add(qr.getQuestion().quid);
        }
        return quids;
    }

    public static List<ISurveyResponse> truncateResponses(List<ISurveyResponse> surveyResponses, ISurveyResponse surveyResponse) {
        List<ISurveyResponse> retval = new ArrayList<ISurveyResponse>();
        for (final ISurveyResponse sr : surveyResponses) {
            final Set<String> answeredQuestions = new HashSet(getQuestionIds(surveyResponse.getResponses()));
            final Set<String> targetResponses = new HashSet(getQuestionIds(sr.getResponses()));
            if (targetResponses.containsAll(answeredQuestions)) {
                retval.add(new ISurveyResponse() {
                    @Override
                    public List<IQuestionResponse> getResponses() {
                        List<IQuestionResponse> retval = new ArrayList<IQuestionResponse>();
                        for (IQuestionResponse qr : sr.getResponses()) {
                            if (answeredQuestions.contains(qr.getQuestion().quid))
                                retval.add(qr);
                        }
                        return retval;
                    }

                    @Override
                    public void setResponses(List<IQuestionResponse> responses) {

                    }

                    @Override
                    public boolean isRecorded() {
                        return true;
                    }

                    @Override
                    public void setRecorded(boolean recorded) {

                    }

                    @Override
                    public String getSrid() {
                        return null;
                    }

                    @Override
                    public void setSrid(String srid) {

                    }

                    @Override
                    public String workerId() {
                        return null;
                    }

                    @Override
                    public Map<String, IQuestionResponse> resultsAsMap() {
                        return null;
                    }

                    @Override
                    public List<ISurveyResponse> readSurveyResponses(Survey s, Reader r) throws SurveyException {
                        return null;
                    }

                    @Override
                    public void setScore(double score) {

                    }

                    @Override
                    public double getScore() {
                        return 0;
                    }

                    @Override
                    public void setThreshold(double pval) {

                    }

                    @Override
                    public double getThreshold() {
                        return 0;
                    }

                    @Override
                    public boolean surveyResponseContainsAnswer(List<Component> variants) {
                        return false;
                    }

                    @Override
                    public KnownValidityStatus getKnownValidityStatus() {
                        return sr.getKnownValidityStatus();
                    }

                    @Override
                    public void setKnownValidityStatus(KnownValidityStatus validityStatus) {
                        // does nothing.
                    }
                });
            }
        }
        return retval;
    }

    public static List<List<ISurveyResponse>> generateBootstrapSample(List<ISurveyResponse> responseList, int iterations) {
        List<List<ISurveyResponse>> retval = new ArrayList<List<ISurveyResponse>>();
        for (int i = 0; i < iterations; i++) {
            List<ISurveyResponse> sample = new ArrayList<ISurveyResponse>();
            for (int j = 0 ; j < responseList.size() ; j++) {
                sample.add(responseList.get(Interpreter.random.nextInt(responseList.size())));
            }
            retval.add(sample);
        }
        return retval;
    }

    /**
     * Returns true if the response is valid, on the basis of the log likelihood.
     * @param survey The survey these respondents answered.
     * @param sr The survey response we are classifying.
     * @param responses The list of actual or simulated responses to the survey
     * @param smoothing Boolean indicating whether we should smooth our calculation of answer frequencies.
     * @param alpha The cutoff used for determining whether a likelihood is too low (a percentage of area under the curve).
     * @return
     */
    public static boolean logLikelihoodClassification(Survey survey, ISurveyResponse sr, List<ISurveyResponse> responses,
                                                      boolean smoothing, double alpha) {
        Map<String, Map<String, Double>> probabilities = makeProbabilities(makeFrequencies(responses, smoothing ? survey : null));
        List<Double> lls = calculateLogLikelihoods(truncateResponses(responses, sr), probabilities);
        if (new HashSet<Double>(lls).size() > 5) {
            double thisLL = getLLForResponse(sr, probabilities);
            List<List<ISurveyResponse>> bsSample = generateBootstrapSample(responses, 500);
            List<Double> means = new ArrayList<Double>();
            for (List<ISurveyResponse> sample : bsSample) {
                double total = 0.0;
                for (ISurveyResponse surveyResponse: sample) {
                    total += getLLForResponse(surveyResponse, probabilities);
                }
                means.add(total / sample.size());
            }
            Collections.sort(means);
            assert means.get(0) < means.get(means.size() - 1);
            SurveyMan.LOGGER.info(String.format("Range of means: [%f, %f]", means.get(0), means.get(means.size() -1)));
            double threshHold = means.get((int) Math.floor(alpha * means.size()));
            SurveyMan.LOGGER.info(String.format("Threshold: %f\tLL: %f", threshHold, thisLL));
            sr.setScore(thisLL);
            return thisLL > threshHold;
        } else return true;
    }

    /**
     * Return true if the response is valid, on the basis of an entropy-based metric.
     * @param survey The survey these respondents answered.
     * @param sr The survey response we are classifying.
     * @param responses The list of actual or simulated responses to the survey
     * @param smoothing Boolean indicating whether we should smooth our calculation of answer frequencies.
     * @param alpha The cutoff used for determining whether a likelihood is too low (a percentage of area under the curve).
     * @return
     */
    public static boolean entropyClassification(Survey survey, ISurveyResponse sr, List<ISurveyResponse> responses,
                                         boolean smoothing, double alpha) {
        // basically the same as logLikelihood, but scores are p * log p, rather than straight up p
        Map<String, Map<String, Double>> probabilities = makeProbabilities(makeFrequencies(responses, smoothing ? survey : null));
        List<Double> lls = calculateLogLikelihoods(truncateResponses(responses, sr), probabilities);
        if (new HashSet<Double>(lls).size() > 5) {
            double thisEnt = getEntropyForResponse(sr, probabilities);
            List<List<ISurveyResponse>> bsSample = generateBootstrapSample(responses, 200);
            List<Double> means = new ArrayList<Double>();
            for (List<ISurveyResponse> sample : bsSample) {
                assert sample.size() > 0 : "Sample size must be greater than 0.";
                double total = 0.0;
                for (ISurveyResponse surveyResponse : sample) {
                    double ent = getEntropyForResponse(surveyResponse, probabilities);
                    total += ent;
                }
                means.add(total / sample.size());
            }
            Collections.sort(means);
            assert means.get(0) < means.get(means.size() - 1) :
                    String.format("Ranked means expected mean at position 0 to be greater than the mean at %d (%f < %f).",
                    means.size(), means.get(0), means.get(means.size() - 1));
            double threshHold = means.get((int) Math.floor(alpha * means.size()));
            sr.setScore(thisEnt);
            SurveyMan.LOGGER.debug(String.format("This entropy: %f\tThis threshold:%f", thisEnt, threshHold));
            return thisEnt < threshHold;
        } else return true;
    }

    public static boolean lpoClassification(Survey survey, ISurveyResponse sr, List<ISurveyResponse> responses) {
        return true;
    }
    //public double calculateBonus(ISurveyResponse sr, Record record);
    //public double getBotThresholdForSurvey(Survey s);

    /**
     * Simulates a survey of 100% random uniform respondents over sampleSize and calculates a prior on false correlation.
     * @param survey The survey these respondents answered.
     * @param sampleSize The sample size the survey writer intends to use during the full-scale study.
     * @param alpha The cutoff used for determining correlation.
     * @return Empirical false correlation.
     * @throws SurveyException
     */
    public static double getProbabilityOfFalseCorrelation(Survey survey, int sampleSize, double alpha) throws SurveyException {
        double p = 0.0;
        List<RandomRespondent> randomRespondents = new ArrayList<RandomRespondent>();
        for (int i = 0 ; i < sampleSize; i++){
            randomRespondents.add(new RandomRespondent(survey, RandomRespondent.AdversaryType.UNIFORM));
        }
        for (Question q1 : survey.questions) {
            for (Question q2: survey.questions) {
                // get responses having answered both questions
                // compute the appropriate correlation coefficient
                // count how many p-values are below the threshhold.
            }
        }
        return p;
    }
}
