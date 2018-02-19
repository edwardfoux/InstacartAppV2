package com.example.edwardfoux.instacartquiz.quiz;

public interface QuizView {

    void onNewQuizAvailable(Quiz quiz);
    void onCorrectItemSelected();
    void onWrongItemSelected();

    void startCountDownService(long remainingTime);
    void finishCountDownService();
    void updateCounter(int value);
    void showNetworkError();
    long getSystemTIme();
}
