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
    }

    ;

    public enum Type {
        Question,
        Choice,
        Range
    }

    ;

    public String prompt;
    public Type type;

    public int minValue;
    public int maxValue;
    public String multipleChoices[];

    public abstract Result show();
};
