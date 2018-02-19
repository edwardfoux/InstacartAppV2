package com.example.edwardfoux.instacartquiz.quiz;
import android.util.Log;

import com.example.edwardfoux.instacartquiz.repository.QuizApi;
import com.example.edwardfoux.instacartquiz.repository.QuizDataService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

class QuizPresenter {

    private static final String TAG = "QuizPresenter";
    private QuizView quizView;
    private int correctAnswer = - 1;
    private boolean isQuizActive;
    private Quiz currentQuiz;
    private int timeLeft = -1;

    private static final int MISSING_ANSWER = -2;

    QuizPresenter(QuizView quizView) {
        this.quizView = quizView;
    }

    void onViewCreated() {
        timeLeft = TimerService.QUIZ_INTERVAL;
        loadQuizData();
    }

    /**
     * Restores previous quiz session if the quiz hasn't timed out
     * @param quiz previous quiz
     * @param correctAnswer correct answer for the previous quiz
     * @param remainingTime remaining time in the previous session
     * @param prevUTCTime utc time during the end of previous session
     */
    void onViewCreated(Quiz quiz, int correctAnswer, int remainingTime, long prevUTCTime) {
        if (quizView.getSystemTIme() > (prevUTCTime + remainingTime)) {
            onViewCreated();
        } else {
            this.correctAnswer = correctAnswer;
            this.currentQuiz = quiz;
            this.timeLeft = remainingTime;
            long timeForQuiz = (prevUTCTime + remainingTime) - quizView.getSystemTIme();
            startQuiz(quiz, (int) timeForQuiz);
        }
    }

    void onDestroy() {
        quizView.finishCountDownService();
    }

    void onItemSelected(int selectedItem) {
        if (selectedItem == correctAnswer) {
            quizView.onCorrectItemSelected();
        } else {
            quizView.onWrongItemSelected();
        }
        quizView.finishCountDownService();
        isQuizActive = false;
        currentQuiz = null;
        timeLeft = -1;
    }

    boolean isQuizActive() {
        return isQuizActive;
    }

    int getRemainingTime() {
        return timeLeft;
    }

    int getCorrectAnswer() {
        return correctAnswer;
    }

    Quiz getQuiz() {
        if (currentQuiz == null) {
            throw new RuntimeException("Quiz should not be null!");
        }
        return currentQuiz;
    }

    private void loadQuizData() {
        QuizDataService quizApi = QuizApi.getQuizDataService();

        quizApi
                .getQuizes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processData, error -> {
                    Log.e(TAG, "error loading data");
                    quizView.showNetworkError();
                });
    }

    private void processData(String jsonResponse) {
        try {
            JSONObject jsonObject = convertToJson(jsonResponse);
            List<String> items = getKeys(jsonObject);
            currentQuiz = selectRandomItem(items, jsonObject);
            correctAnswer = QuizUtil.randomizeAnswerOptions(currentQuiz);
            startQuiz(currentQuiz, TimerService.QUIZ_INTERVAL);

        } catch (JSONException e) {
            Log.e(TAG, "error parsing data: "+ e.toString());
        }
    }

    private void startQuiz(Quiz quiz, int remainingTime) {
        quizView.onNewQuizAvailable(quiz);
        quizView.startCountDownService(remainingTime);
        isQuizActive = true;

        BroadcastRelay
                .getInstance()
                .getRelay()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(value -> {
                    timeLeft = remainingTime - value.intValue();
                    if (timeLeft < 1) {
                        onItemSelected(MISSING_ANSWER);
                    }
                    quizView.updateCounter(timeLeft);
                });
    }

    private JSONObject convertToJson(String jsonResponse) throws JSONException{
        return new JSONObject(jsonResponse);
    }

    private List<String> getKeys(JSONObject object) throws JSONException {
        List<String> list = new ArrayList<>();
        Iterator<?> keys = object.keys();

        while ( keys.hasNext() ) {
            String key = (String) keys.next();
            list.add(key);
        }

        return list;
    }

    private Quiz selectRandomItem(List<String> keys, JSONObject jsonObject) throws JSONException {
        int size = keys.size();
        if (size == 0) {
            return null;
        }
        Quiz quiz = new Quiz();
        Random random = new Random();
        int randomItem = random.nextInt(keys.size());

        String randomKey = keys.get(randomItem);

        JSONArray jsonQuiz = jsonObject.getJSONArray(randomKey);

        quiz.setQuizName(randomKey);

        List<String> options = new ArrayList<>();

        for (int i = 0; i < jsonQuiz.length(); i++) {
            options.add(jsonQuiz.getString(i));
        }

        quiz.setQuizOptions(options);
        return quiz;
    }
}
