package org.oreland.ui;

/**
 * Created by jonas on 10/4/15.
 */
public abstract class Dialog {

    public Dialog(Type type, String question) {
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

    public String prompt;
    public Type type;

    public int minValue;
    public int maxValue;
    public String choices[];

    public abstract Result show();
};
