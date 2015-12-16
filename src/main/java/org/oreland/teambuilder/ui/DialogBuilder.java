package org.oreland.teambuilder.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by jonas on 10/4/15.
 */
public class DialogBuilder {

    String question;
    String choices[];
    int minValue, maxValue;
    Dialog.Type type = Dialog.Type.Question;

    public DialogBuilder() {
    }

    public void setQuestion(String s) {
        question = s;
    }

    public void setChoices(String choices[]) {
        type = Dialog.Type.Choice;
        this.choices = choices;
    }

    public void setMultiChoices(String choices[]) {
        type = Dialog.Type.MultiChoice;
        this.choices = choices;
    }

    public Dialog build() {
        switch (type) {
            case Question:
                return new Question(question);
            case Choice:
                return new Choice(question, choices);
            case MultiChoice:
                return new MultiChoice(question, choices);
            case Range:
                return new Range(question, minValue, maxValue);
        }
        return null;
    }

    public void setChoices(List<String> choices) {
        String tmp[] = new String[choices.size()];
        choices.toArray(tmp);
        setChoices(tmp);
    }

    public void setMultiChoices(List<String> choices) {
        String tmp[] = new String[choices.size()];
        choices.toArray(tmp);
        setMultiChoices(tmp);
    }

    public void setRange(int minValue, int maxValue) {
        type = Dialog.Type.Range;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
}

class Question extends Dialog {

    public Question(String question) {
        super(Type.Question, question);
    }

    @Override
    public Result show() {
        System.out.print(prompt);
        Result result = Result.OK;
        try {
            result.stringResult = new BufferedReader(new InputStreamReader(System.in)).readLine();
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.CANCEL;
    }
}

class Choice extends Dialog {

    public Choice(String question, String[] choices) {
        super(Type.Choice, question);
        this.choices = choices;
    }

    @Override
    public Result show() {
        System.out.println(prompt);
        int pos = 1;
        for (String s : choices) {
            System.out.println(pos + ") " + s);
            pos++;
        }
        System.out.print("Select a number: ");
        Result result = Result.OK;
        result.intResult = new Scanner(System.in).nextInt();
        return result;
    }
}

class Range extends Dialog {

    int minValue;
    int maxValue;

    public Range(String question, int minValue, int maxValue) {
        super(Type.Range, question);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public Result show() {
        System.out.println(prompt);
        Result result = Result.OK;
        result.intResult = new Scanner(System.in).nextInt();
        return result;
    }
}

class MultiChoice extends Dialog {

    public MultiChoice(String question, String[] choices) {
        super(Type.MultiChoice, question);
        this.choices = choices;
    }

    @Override
    public Result show() {
        System.out.println(prompt);
        int pos = 1;
        for (String s : choices) {
            System.out.println(pos + ") " + s);
            pos++;
        }
        System.out.print("Choose items (separated by space): ");
        Result result = Result.CANCEL;
        try {
            List<Integer> list = new ArrayList<Integer>();
            Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(System.in)).readLine());
            while (scanner.hasNextInt()) {
                Integer i = scanner.nextInt();
                list.add(i);
            }
            int res[] = new int[list.size()];
            pos = 0;
            for (Integer i : list) {
                res[pos++] = i;
            }
            result = Result.OK;
            result.intResults = res;
        } catch (Exception ex) {
        }
        return result;
    }
}
