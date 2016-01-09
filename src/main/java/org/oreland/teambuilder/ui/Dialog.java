package org.oreland.teambuilder.ui;

/**
 * Created by jonas on 10/4/15.
 */
public abstract class Dialog {

    Dialog(Type type, String question) {
        this.type = type;
        this.prompt = question;
    }

    public enum Result {
        OK,
        CANCEL;

        public String stringResult;
        public int intResult;
        public int intResults[];
    }

    public enum Type {
        Question,
        Choice,
        Range,
        MultiChoice
    }

    String prompt;
    private Type type;
    String[] choices;

    public abstract Result show();
}
