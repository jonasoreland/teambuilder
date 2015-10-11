package org.oreland.ui;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Created by jonas on 10/4/15.
 */
public class DialogBuilder {

    String question;
    String multipleChoices[];
    Dialog.Type type = Dialog.Type.Question;

    public DialogBuilder() {
    }

    public void setQuestion(String s) {
        question = s;
    }

    public void setMultipleChoices(String choices[]) {
        type = Dialog.Type.Choice;
        multipleChoices = choices;
    }

    public Dialog build() {
        switch (type) {
            case Question:
                return new Question(question);
            case Choice:
                return new MultiChoice(question, multipleChoices);
            case Range:
                break;
        }
        return null;
    }

    public void setMultipleChoices(List<String> choices) {
        String tmp[] = new String[choices.size()];
        choices.toArray(tmp);
        setMultipleChoices(tmp);
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
            result.stringResult = new DataInputStream(System.in).readLine();
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.CANCEL;
    }
}

class MultiChoice extends Dialog {

    public MultiChoice(String question, String[] multipleChoices) {
        super(Type.Choice, question);
        this.multipleChoices = multipleChoices;
    }

    @Override
    public Result show() {
        System.out.println(prompt);
        int pos = 1;
        for (String s : multipleChoices) {
            System.out.println(pos + ") " + s);
            pos++;
        }
        System.out.print("Select a number: ");
        Result result = Result.OK;
        result.intResult = new Scanner(System.in).nextInt();
        return result;
    }
}