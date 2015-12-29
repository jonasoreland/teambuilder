package org.oreland.teambuilder.ui;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.oreland.teambuilder.Interactive;
import org.oreland.teambuilder.sync.Synchronizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

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

    public static Synchronizer.Specifier selectOne(DialogBuilder builder,
                                                   String prompt, List<Synchronizer.Specifier> choices) {
        Set<String> valueset = new HashSet<>();
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Synchronizer.Specifier e : choices) {
            if (!valueset.contains(e.key)) {
                valueset.add(e.key);
                names.add(e.name);
                values.add(e.key);
            }
        }

        builder.setQuestion(prompt);
        builder.setChoices(names);
        Dialog.Result result = builder.build().show();
        return choices.get(result.intResult - 1);
    }

    static public List<Synchronizer.Specifier> selectMulti(DialogBuilder builder, String prompt, List<Synchronizer.Specifier> choices,
                                                           boolean addAllOption) {
        Set<String> nameset = new HashSet<>();
        List<String> names = new ArrayList<>();
        for (Synchronizer.Specifier e : choices) {
            if (!nameset.contains(e.name)) {
                nameset.add(e.name);
                names.add(e.name);
            }
        }

        if (addAllOption) {
            names.add("ALL");
        }

        builder.setQuestion(prompt);
        builder.setMultiChoices(names);
        Dialog.Result result = builder.build().show();
        List<Synchronizer.Specifier> returnValue = new ArrayList<>();
        if (addAllOption) {
            if (contains(result.intResults, names.size())) {
                returnValue.addAll(choices);
                return returnValue;
            }
        }
        for (int res : result.intResults) {
            String name = names.get(res - 1);
            for (Synchronizer.Specifier spec : choices) {
                if (spec.name.contentEquals(name))
                    returnValue.add(spec);
            }
        }
        return returnValue;
    }

    private static boolean contains(int[] intResults, int value) {
        for (int i : intResults) {
            if (i == value)
                return true;
        }
        return false;
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
