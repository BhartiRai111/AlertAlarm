package com.bharti.alertalarm;

import android.content.Context;

import java.util.Random;

/** Short motivation lines for the alarm screen (Hindi when the voice language is Hindi). */
final class Quotes {

    private static final String[] ENGLISH = {
            "Small steps every day add up to big results.",
            "You don't have to feel ready. You just have to start.",
            "Focus on the next 10 minutes. Then the next 10.",
            "Discipline is doing it even when you don't feel like it.",
            "The phone can wait. Your future can't.",
            "One hour of focus beats five hours of distraction.",
            "Done is better than perfect.",
            "Future you is watching. Make them proud.",
            "Start where you are. Use what you have. Do what you can.",
            "Every expert was once a beginner who didn't quit.",
            "Motivation gets you started. Habit keeps you going.",
            "You are one task closer to your goal.",
            "Hard work today, easy life tomorrow.",
            "Consistency is more important than intensity.",
            "Stop scrolling. Start doing.",
            "Your dream job is built one study session at a time.",
            "It's not about having time. It's about making time.",
            "A little progress each day is still progress.",
            "Don't count the hours. Make the hours count.",
            "Push now, relax later.",
            "Success is the sum of small efforts repeated daily.",
            "The best time to start was yesterday. The next best is now.",
            "Believe in yourself and get to work.",
            "You made this plan for a reason. Trust it.",
            "Be stronger than your excuses.",
    };

    private static final String[] HINDI = {
            "रोज़ का छोटा कदम ही बड़ी मंज़िल तक ले जाता है।",
            "शुरू करने के लिए तैयार महसूस करना ज़रूरी नहीं, बस शुरू करो।",
            "फ़ोन बाद में, सपने पहले।",
            "आज की मेहनत, कल का आराम।",
            "बहाने नहीं, काम बोलेगा।",
            "हर दिन थोड़ा आगे, बस यही काफ़ी है।",
            "अगले 10 मिनट पर ध्यान दो, फिर अगले 10 पर।",
            "अनुशासन वही है जो मन न होने पर भी काम कराए।",
            "तुम्हारा प्लान तुमने ही बनाया है, उस पर भरोसा रखो।",
            "लगातार कोशिश ही असली ताक़त है।",
            "सपनों की नौकरी एक-एक पढ़ाई के घंटे से बनती है।",
            "रुकना नहीं है, बस चलते रहना है।",
            "समय मिलता नहीं, निकालना पड़ता है।",
            "आज जो करोगे, कल वही दिखेगा।",
            "अपने बहानों से ज़्यादा मज़बूत बनो।",
    };

    private static final Random RANDOM = new Random();

    private Quotes() {
    }

    static String random(Context c) {
        String[] list = "hi".equals(AppSettings.voiceLanguage(c)) ? HINDI : ENGLISH;
        return list[RANDOM.nextInt(list.length)];
    }
}
