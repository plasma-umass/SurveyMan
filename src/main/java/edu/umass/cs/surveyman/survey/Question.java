package edu.umass.cs.surveyman.survey;

import edu.umass.cs.surveyman.SurveyMan;
import edu.umass.cs.surveyman.input.AbstractParser;
import edu.umass.cs.surveyman.input.csv.CSVParser;
import edu.umass.cs.surveyman.input.exceptions.BranchException;
import edu.umass.cs.surveyman.survey.exceptions.SurveyException;

import java.util.*;
import java.util.regex.Pattern;


/**
 * The class representing a Question object. The Question object includes instructional "questions."
 */
public class Question extends SurveyObj {

    private static int QUESTION_COL = 0;
    private static int nextRow = 0;

    /**
     * Determines whether the input question id corresponds to a known custom question pattern. Custom questions are
     * those that the user plugs in after parsing the input survey. They typically encompass ad hoc data such as timing
     * information and custom freetext questions added in the course of debugging.
     *
     * @param quid The identifier of the question we are testing.
     * @return boolean indicating whether the question has a known custom question id pattern.
     */
    public static boolean customQuestion(String quid) {
        return quid.startsWith("custom") || quid.contains("-1");
    }

    /**
     * Thrown by the Parser. It should never be thrown when surveys are constructed programmatically.
     */
    public static class MalformedOptionException extends SurveyException {
        public MalformedOptionException(String msg) {
            super(msg);
        }
    }

    /**
     * Thrown whenever the user or system attempts to find an option by text, input cell, etc. and that option does
     * not exist for this question object.
     */
    public static class OptionNotFoundException extends SurveyException {
        public OptionNotFoundException(String oid, String quid){
            super(String.format("Option %s not found in Question %s", oid, quid));
        }
    }

    /**
     * Unique question identifier. Typically generated upon parsing.
     */
    public String quid;
    /**
     * Data to be displayed when the user takes the survye.
     */
    public Component data;
    /**
     * Answer to the question, if it exists.
     */
    public Component answer;
    /**
     * Map from component identifiers to answer option objects ({@link edu.umass.cs.surveyman.survey.Component}).
     */
    public Map<String, Component> options = new HashMap<String, Component>();
    /**
     * Map from answer options to branch destinations ({@link edu.umass.cs.surveyman.survey.Block}). This may be left
     * empty if there is no branching.
     */
    protected BranchMap branchMap = new BranchMap();
    /**
     * Source data line numbers corresponding to this question. Used for parsing and debugging.
     */
    public List<Integer> sourceLineNos = new ArrayList<Integer>();
    /**
     * Map from other input column headers to their values, when they exist for this question.
     */
    public Map<String, String> otherValues = new HashMap<String, String>();
    /**
     * The enclosing block for this question.
     */
    public Block block;
    /**
     * True if respondents may only answer one of the answer options.
     */
    public Boolean exclusive;
    /**
     * True if the answer options have a natural ordering (e.g., Likert scales).
     */
    public Boolean ordered;
    /**
     * True if the answer options may be randomized. If the ordered field is true, then there are only two possible
     * permutations. If the ordered field is false, there are factorial permutations in the number of options.
     */
    public Boolean randomize = true;
    /**
     * True if this question requires a text response.
     */
    public Boolean freetext;
    /**
     * Set if this question requires a text response and must conform to a regular expression.
     */
    public Pattern freetextPattern;
    /**
     * Set if this question requires a text response and should display example text.
     */
    public String freetextDefault;
    /**
     * Indicates whether respondents may submit their results immediately after answering this questions, regardless of
     * its position in the survey.
     */
    public boolean permitBreakoff = true;
    /**
     * A correlation label.
     */
    public String correlation = "";

    /**
     * Creates a question identifier corresponding to the input data location.
     * @param row This question's initial input row index.
     * @param col The question column index.
     * @return A unique identifier based on input location.
     */
    public static String makeQuestionId(int row, int col) {
        return String.format("q_%d_%d", row, col);
    }

    private Question(int row, int col){
        this.quid = makeQuestionId(row, col);
    }

    /**
     * Used by the {@link edu.umass.cs.surveyman.input} parsers.
     * @param data The parsed, internal representation of the data in a cell.
     * @param row The input row (literal or calculated, as with JSON).
     * @param col The input column (literal or calculated, as with JSON).
     */
    public Question(Component data, int row, int col) {
        this(row, col);
        this.data = data;
    }

    /**
     * Creates a question whose identifier is based on the question's input location and whose associated data
     * {@link edu.umass.cs.surveyman.survey.Component} is {@param data}.
     *
     * @param data The data associated with this question.
     * @param row This question's initial input row index.
     * @param col The question column index.
     */
    public Question(String data, int row, int col) {
        this(row, col);
        if (HTMLComponent.isHTMLComponent(data))
            this.data = new HTMLComponent(data, row, col);
        else this.data = new StringComponent(data, row, col);
    }

    public Question(String data, boolean ordered, boolean exclusive) {
        this(data, Question.nextRow, QUESTION_COL);
        this.ordered = ordered;
        this.exclusive = exclusive;
        this.freetext = false;
    }

    /**
     * Constructor for the programmatic creation of questions.
     * @param data The data associated with this question.
     */
    public Question(String data) {
        this(data, false, true);
    }

    private int countLines() {
        int optLines = this.options.size();
        if (optLines == 0)
            return 1;
        else return optLines;
    }

    protected void updateFromSurvey(Survey s) {
        assert !s.questions.contains(this);
        int otherRows = 0;
        for (Question q : s.questions) {
            otherRows += q.countLines();
        }
        this.quid = makeQuestionId(otherRows+1, Question.QUESTION_COL);
    }

    public void addOption(String surfaceText) throws SurveyException {
        int sourceRow = this.getSourceRow() + this.options.size();
        if (HTMLComponent.isHTMLComponent(surfaceText))
            this.addOption(new HTMLComponent(surfaceText, sourceRow, Component.DEFAULT_SOURCE_COL));
        else this.addOption(new StringComponent(surfaceText, sourceRow, Component.DEFAULT_SOURCE_COL));
    }

    public void addOptions(String... surfaceTexts) throws SurveyException {
        for (String s : surfaceTexts) {
            this.addOption(s);
        }
    }

    public void addOption(Component component) throws BranchException {
        if (this.isBranchQuestion() || (this.block != null && this.block.branchParadigm.equals(Block.BranchParadigm.ALL)))
            throw new BranchException("This question is a branch question.");
        if (this.options.containsKey(component.getCid()))
            SurveyMan.LOGGER.warn("Attempted to add option " + component + " more than once.");
        else {
            component.index = this.options.size();
            this.options.put(component.getCid(), component);
            this.sourceLineNos.add(component.getSourceRow());
            nextRow += (component.getSourceRow() - nextRow);
        }
    }

    public void addOption(Component component, Block branchTo) throws BranchException {
        if (this.block == null || this.equals(this) || this.block.branchParadigm.equals(Block.BranchParadigm.ALL)) {
            if (this.options.containsKey(component.getCid()))
                SurveyMan.LOGGER.warn("Attempted to add option " + component + " more than once.");
            else {
                this.options.put(component.getCid(), component);
                component.index = this.options.size();
                nextRow += (component.getSourceRow() - nextRow);
                this.sourceLineNos.add(component.getSourceRow());
            }
            this.branchMap.put(component, branchTo);
        } else throw new BranchException("This question is not a branch question.");

    }

    public Set<Block> getBranchDestinations() {
        Set<Block> retval = new HashSet<Block>();
        for (Block b : this.branchMap.values())
            retval.add(b);
        return retval;
    }

    public boolean isBranchQuestion() {
        return !this.branchMap.isEmpty();
    }

    public Block getBranchDest(Component c) {
        return this.branchMap.get(c);
    }

    /**
     * Returns the answer option associated with this question having the input
     * {@link edu.umass.cs.surveyman.survey.Component} identifier.
     *
     * @param oid The input {@link edu.umass.cs.surveyman.survey.Component} identifier.
     * @return The appropriate {@link edu.umass.cs.surveyman.survey.Component} subclass.
     * @throws edu.umass.cs.surveyman.survey.Question.OptionNotFoundException if there is no answer option associated
     * with this question.
     */
    public Component getOptById(String oid) throws SurveyException {
        if (oid.equals("comp_-1_-1"))
            return null;
        if (options.containsKey(oid))
            return options.get(oid);
        throw new OptionNotFoundException(oid, this.quid);
    }

    /**
     * Returns a sorted array of the answer options.
     * @return {@link edu.umass.cs.surveyman.survey.Component} array of the answer options, sorted by their relative
     * indices.
     * @throws edu.umass.cs.surveyman.survey.Question.MalformedOptionException if there is an error with the options'
     * indices.
     */
    public Component[] getOptListByIndex() throws SurveyException {
        if (freetext) return new Component[0];
        Component[] opts = new Component[options.size()];
        for (Component c : options.values())
            if (c.index > options.size())
                throw new MalformedOptionException(String.format("Option \r\n{%s}\r\n has an index that exceeds max index %d"
                        , c.toString()
                        , options.size() - 1));
            else if (opts[c.index] != null)
                throw new MalformedOptionException(String.format("Options \r\n{%s}\r\n and \r\n{%s}\r\n have the same index. " +
                        "(Entries (%d, %d) and (%d, %d) both have index %d)."
                        , opts[c.index]
                        , c.toString()
                        , opts[c.index].getSourceRow(), opts[c.index].getSourceCol()
                        , c.getSourceRow(), c.getSourceCol(), c.index
                        )
                    );
            else
                opts[c.index] = c;
         return opts;
    }

    /**
     * Tests whether this question precedes the input question, according to their enclosing blocks. See
     * {@link edu.umass.cs.surveyman.survey.Block before}.
     *
     * @param q Question to compare.
     * @return {@code true} if the input question follows this question. {@code false} if randomization and/or partial
     * ordering cannot determine a strict ordering.
     */
    public boolean before(Question q) {
        int[] myBLockID = this.block.getBlockId();
        for (int i = 0 ; i < myBLockID.length ; i++) {
            if (i >= q.block.getBlockId().length)
                return false; // can't say it's strictly before
            else if (myBLockID[i] < q.block.getBlockId()[i])
                return true;
        }
        return false;
    }

    /**
     * Getter for the input source line.
     * @return {@code int} corresponding to the first input source line.
     */
    public int getSourceRow() {
        return Integer.parseInt(quid.split("_")[1]);
    }

    /**
     * Getter for the input source column. This should be the same for every question in a survey.
     * @return {@code int} corresponding to the QUESTION column.
     */
    public int getSourceCol() {
        return Integer.parseInt(quid.split("_")[2]);
    }

    /**
     * For each question, returns the set of all equivalent questions (including itself).
     * @return The list of equivalent questions. If this question is not part of a set of variants, the function will
     * return a list containing just this question. If this question is a custom question, the function will return an
     * empty list.
     */
    public List<Question> getVariants() {
        List<Question> questions = new ArrayList<Question>();
        if (!customQuestion(this.quid)) {
            if (this.block.branchParadigm == Block.BranchParadigm.ALL)
                return this.block.questions;
            else {
                questions.add(this);
            }
        }
        return questions;
    }

    private String getFreetextValue() {
        if ( this.freetextDefault != null )
            return String.format("\"%s\"", this.freetextDefault);
        else if ( this.freetextPattern != null )
            return String.format("\"#{%s}\"", this.freetextPattern.pattern());
        else return "true";
    }

    protected String jsonize() throws SurveyException {

        String options = Component.jsonize(Arrays.asList(this.getOptListByIndex()));
        String branchMap = this.branchMap.jsonize();
        StringBuilder qtext = new StringBuilder();
        StringBuilder otherStuff = new StringBuilder();

        qtext.append(Component.html(this.data));

        if (options.equals(""))
            otherStuff.append(this.freetext ? String.format(", \"freetext\" : %s", this.getFreetextValue()) : "");
        else otherStuff.append(String.format(", \"options\" : %s", options));

        if (!branchMap.equals(""))
            otherStuff.append(String.format(", \"branchMap\" : %s ", branchMap));

        if (this.randomize != CSVParser.defaultValues.get(AbstractParser.RANDOMIZE).booleanValue())
            otherStuff.append(String.format(", \"randomize\" : %s", this.randomize));

        if (this.ordered != CSVParser.defaultValues.get(AbstractParser.ORDERED).booleanValue())
            otherStuff.append(String.format(", \"ordered\" : %s", this.ordered));

        if (this.exclusive != CSVParser.defaultValues.get(AbstractParser.EXCLUSIVE).booleanValue())
            otherStuff.append(String.format(", \"exclusive\" : %s", this.exclusive));

        if (!this.permitBreakoff)
            otherStuff.append( ", \"breakoff\" : false");

        if (!this.correlation.equals(""))
            otherStuff.append(String.format(", \"correlation\" : \"%s\"", this.correlation));

        if (this.answer != null)
            otherStuff.append(String.format(", \"answer\" : \"%s\"", this.answer.getCid()));

        return String.format("{ \"id\" : \"%s\", \"qtext\" : \"%s\" %s}"
                , this.quid
                , qtext.toString()
                , otherStuff.toString());
    }

    protected static String jsonize(List<Question> questionList) throws SurveyException {
        Iterator<Question> qs = questionList.iterator();
        if (!qs.hasNext())
            return "[]";
        StringBuilder s = new StringBuilder(qs.next().jsonize());
        while (qs.hasNext()) {
            Question q = qs.next();
            s.append(String.format(", %s", q.jsonize()));
        }
        return String.format("[ %s ]", s.toString());
    }

    private static void makeQuestions(Question[] questions, String... surfaceStrings) {
        assert questions.length == surfaceStrings.length;
        for (int i = 0; i < questions.length; i++) {
            questions[i] = new Question(surfaceStrings[i]);
        }
    }

    /**
     * Convenience method for quickly creating a series of questions with the default settings.
     * @param questions An array that will be populated with new question objects.
     * @param surfaceStrings The strings corresponding to the text the user will see.
     */
    public static void makeUnorderedRadioQuestions(Question[] questions, String... surfaceStrings) {
        makeQuestions(questions, surfaceStrings);
    }

    public static void makeOrderedRadioQuestions(Question[] questions, String... surfaceStrings) {
        makeQuestions(questions, surfaceStrings);
        for (Question q : questions) {
            q.ordered = true;
        }
    }

    public static void makeUnorderedCheckQuestions(Question[] questions, String... surfaceStrings) {
        makeQuestions(questions);
        for (Question q: questions) {
            q.exclusive = false;
        }
    }

    /**
     * Returns a string of the question data.
     * @return String of the data field.
     */
    @Override
    public String toString() {
        return data.toString();
    }

    /**
     * Two questions are equal if the following are equal:
     * <p>
     *     <ul>
     *         <li>quid</li>
     *         <li>data</li>
     *         <li>option map</li>
     *         <li>enclosing block</li>
     *         <li>exclusive flag</li>
     *         <li>ordered flag</li>
     *         <li>randomize flag</li>
     *     </ul>
     * </p>
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o){
        assert(o instanceof Question);
        Question q = (Question) o;
        return ! this.quid.equals(AbstractParser.CUSTOM_ID)
                && this.data.equals(q.data)
                && this.options.equals(q.options)
                && this.block.equals(q.block)
                && this.exclusive.equals(q.exclusive)
                && this.ordered.equals(q.ordered)
                && this.randomize.equals(q.randomize);
    }

    /**
     * Hashcodes are computed from the quid.
     * @return hashcode
     */
    @Override
    public int hashCode() {
        return this.quid.hashCode();
    }

}
