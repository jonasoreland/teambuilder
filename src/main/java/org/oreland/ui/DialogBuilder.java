package org.oreland.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Scanner;

/**
 * Created by jonas on 10/4/15.
 */
public class DialogBuilder {

    String question;
    String choices[];
    Dialog.Type type = Dialog.Type.Question;

    public DialogBuilder() {
    }

    public void setQuestion(String s) {
        question = s;
    }

    public void setChoices(String choices[]) {
        type = Dialog.Type.Choice;
        choices = choices;
    }

    public Dialog build() {
        switch (type) {
            case Question:
                return new Question(question);
            case Choice:
                return new Choice(question, choices);
            case Range:
                break;
        }
        return null;
    }

    public void setChoices(List<String> choices) {
        String tmp[] = new String[choices.size()];
        choices.toArray(tmp);
        setChoices(tmp);
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
