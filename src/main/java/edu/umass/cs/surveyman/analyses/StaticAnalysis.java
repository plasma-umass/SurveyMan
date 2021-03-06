package edu.umass.cs.surveyman.analyses;

import edu.umass.cs.surveyman.SurveyMan;
import edu.umass.cs.surveyman.qc.Classifier;
import edu.umass.cs.surveyman.qc.QCMetrics;
import edu.umass.cs.surveyman.survey.Survey;
import edu.umass.cs.surveyman.survey.exceptions.SurveyException;
import org.jsoup.select.Evaluator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class StaticAnalysis {

    public static class Report {

        public final double avgPathLength;
        public final double maxPossibleEntropy;
        public final int maxPathLength;
        public final int minPathLength;
        public final double probFalseCorrelation;
        public final List<Simulation.ROC> rocList;

        Report(int minPathLength,
               int maxPathLength,
               double avgPathLength,
               double maxPossibleEntropy,
               double probFalseCorrelation,
               List<Simulation.ROC> rocList) {
            this.avgPathLength = avgPathLength;
            this.maxPossibleEntropy = maxPossibleEntropy;
            this.maxPathLength = maxPathLength;
            this.minPathLength = minPathLength;
            this.probFalseCorrelation = probFalseCorrelation;
            this.rocList = rocList;
        }

        public void print(OutputStream stream) {
            OutputStreamWriter osw = new OutputStreamWriter(stream);
            try {
                osw.write(String.format(
                        "Min Path Length:\t%d\n" +
                        "Max Path Length:\t%d\n" +
                        "Average Path Length:\t%f\n" +
                        "Max Possible Entropy:\t%f\n" +
                        "Prob. False Correlation",
                        this.minPathLength,
                        this.maxPathLength,
                        this.avgPathLength,
                        this.maxPossibleEntropy,
                        this.probFalseCorrelation
                ));
                osw.write("percentBots,entropy,false corr,TP,FP,TN,FN\n");
                for (Simulation.ROC roc : rocList) {
                    osw.write(String.format("%f,%d,%d,%d,%d\n",
                            roc.percBots,
                            roc.empiricalEntropy,
                            roc.truePositive,
                            roc.falsePositive,
                            roc.trueNegative,
                            roc.falseNegative)
                    );
                }
                osw.close();
            } catch (IOException e) {
                SurveyMan.LOGGER.warn(e);
            }
        }
    }

    public static void wellFormednessChecks(Survey survey) throws SurveyException{
        SurveyMan.LOGGER.info(String.format("Testing %d rules...", AbstractRule.getRules().size()));
        for (AbstractRule rule : AbstractRule.getRules()) {
            SurveyMan.LOGGER.info(rule.getClass().getName());
            rule.check(survey);
        }
    }

    public static Report staticAnalysis(
            Survey survey,
            Classifier classifier,
            int n,
            double granularity,
            double alpha) throws SurveyException {
        wellFormednessChecks(survey);
        List<Simulation.ROC> rocList = new ArrayList<Simulation.ROC>();
        for (double percRandomRespondents = 0.0 ; percRandomRespondents <= 1.0 ; percRandomRespondents += granularity) {
            List<ISurveyResponse> srs = Simulation.simulate(survey, 100, percRandomRespondents);
            rocList.add(Simulation.analyze(survey, srs, classifier));
        }
        return new Report(
                QCMetrics.minimumPathLength(survey),
                QCMetrics.maximumPathLength(survey),
                QCMetrics.averagePathLength(survey),
                QCMetrics.getMaxPossibleEntropy(survey),
                QCMetrics.getProbabilityOfFalseCorrelation(survey, n, alpha),
                rocList
        );
    }
}
