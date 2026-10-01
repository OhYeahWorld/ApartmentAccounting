package rea.ohyeahworld.newpgapp.service;

/**
 * Бросается, когда данные не проходят проверку периода / защиты от дублей.
 */
public class DataValidationException extends RuntimeException {
    public DataValidationException(String message) {
        super(message);
    }
}
