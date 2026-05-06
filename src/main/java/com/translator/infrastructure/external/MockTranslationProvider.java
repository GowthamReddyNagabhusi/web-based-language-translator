package com.translator.infrastructure.external;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Last-resort fallback provider. Returns real translations for common phrases
 * from its built-in dictionary; for anything else returns a stub so the API
 * never returns a 500.
 */
@Component
public class MockTranslationProvider implements TranslationProvider {

    private static final Map<String, Map<String, String>> DICTIONARY = Map.ofEntries(
        Map.entry("hello", Map.of("es","Hola","fr","Bonjour","de","Hallo","hi","नमस्ते","ar","مرحبا","zh","你好","ja","こんにちは","ko","안녕하세요","pt","Olá","ru","Привет")),
        Map.entry("hello world", Map.of("es","Hola Mundo","fr","Bonjour le Monde","de","Hallo Welt","hi","नमस्ते दुनिया","zh","你好世界","ja","こんにちは世界")),
        Map.entry("how are you", Map.of("es","¿Cómo estás?","fr","Comment ça va?","de","Wie geht es dir?","hi","आप कैसे हैं?","ar","كيف حالك؟","zh","你好吗","ja","お元気ですか","pt","Como vai você?")),
        Map.entry("hello, how are you today?", Map.of("es","Hola, ¿cómo estás hoy?","fr","Bonjour, comment allez-vous aujourd'hui?","de","Hallo, wie geht es Ihnen heute?","hi","नमस्ते, आज आप कैसे हैं?","ar","مرحبا، كيف حالك اليوم؟")),
        Map.entry("good morning", Map.of("es","Buenos días","fr","Bonjour","de","Guten Morgen","hi","शुभ प्रभात","ar","صباح الخير","zh","早上好","ja","おはようございます","pt","Bom dia","ru","Доброе утро")),
        Map.entry("good night", Map.of("es","Buenas noches","fr","Bonne nuit","de","Gute Nacht","hi","शुभ रात्रि","ar","تصبح على خير","zh","晚安","ja","おやすみなさい","pt","Boa noite","ru","Спокойной ночи")),
        Map.entry("thank you", Map.of("es","Gracias","fr","Merci","de","Danke","hi","धन्यवाद","ar","شكرًا","zh","谢谢","ja","ありがとう","ko","감사합니다","pt","Obrigado","ru","Спасибо")),
        Map.entry("goodbye", Map.of("es","Adiós","fr","Au revoir","de","Auf Wiedersehen","hi","अलविदा","ar","وداعا","zh","再见","ja","さようなら","ko","안녕히 가세요","pt","Adeus","ru","До свидания")),
        Map.entry("yes", Map.of("es","Sí","fr","Oui","de","Ja","hi","हाँ","ar","نعم","zh","是","ja","はい","ko","예","pt","Sim","ru","Да")),
        Map.entry("no", Map.of("es","No","fr","Non","de","Nein","hi","नहीं","ar","لا","zh","不","ja","いいえ","ko","아니요","pt","Não","ru","Нет")),
        Map.entry("please", Map.of("es","Por favor","fr","S'il vous plaît","de","Bitte","hi","कृपया","ar","من فضلك","zh","请","ja","どうぞ","pt","Por favor","ru","Пожалуйста")),
        Map.entry("sorry", Map.of("es","Lo siento","fr","Désolé","de","Entschuldigung","hi","माफ करें","ar","آسف","zh","对不起","ja","すみません","ko","죄송합니다","pt","Desculpe","ru","Извините")),
        Map.entry("i love you", Map.of("es","Te amo","fr","Je t'aime","de","Ich liebe dich","hi","मैं तुमसे प्यार करता हूँ","ar","أحبك","zh","我爱你","ja","愛してる","ko","사랑해","pt","Eu te amo","ru","Я тебя люблю")),
        Map.entry("where is the bathroom?", Map.of("es","¿Dónde está el baño?","fr","Où est la salle de bain?","de","Wo ist das Badezimmer?","hi","बाथरूम कहाँ है?","zh","浴室在哪里?","ja","トイレはどこですか?")),
        Map.entry("what is your name?", Map.of("es","¿Cómo te llamas?","fr","Comment vous appelez-vous?","de","Wie heißen Sie?","hi","आपका नाम क्या है?","ar","ما اسمك؟","zh","你叫什么名字?","ja","お名前は何ですか?")),
        Map.entry("my name is", Map.of("es","Me llamo","fr","Je m'appelle","de","Mein Name ist","hi","मेरा नाम है","ar","اسمي","zh","我叫","ja","私の名前は")),
        Map.entry("help", Map.of("es","Ayuda","fr","Aide","de","Hilfe","hi","मदद","ar","مساعدة","zh","帮助","ja","助けて","ko","도움","pt","Ajuda","ru","Помощь")),
        Map.entry("water", Map.of("es","Agua","fr","Eau","de","Wasser","hi","पानी","ar","ماء","zh","水","ja","水","ko","물","pt","Água","ru","Вода")),
        Map.entry("food", Map.of("es","Comida","fr","Nourriture","de","Essen","hi","खाना","ar","طعام","zh","食物","ja","食べ物","ko","음식","pt","Comida","ru","Еда")),
        Map.entry("i don't understand", Map.of("es","No entiendo","fr","Je ne comprends pas","de","Ich verstehe nicht","hi","मुझे समझ नहीं आया","ar","لا أفهم","zh","我不明白","ja","わかりません","ko","이해하지 못합니다","pt","Não entendo","ru","Я не понимаю"))
    );

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        String key = text.toLowerCase().trim();
        Map<String, String> targets = DICTIONARY.get(key);
        if (targets != null) {
            String tgt = targets.get(targetLang.toLowerCase());
            if (tgt != null) return tgt;
        }
        // Generic stub — never causes a 500
        return "[" + targetLang.toUpperCase() + "] " + text;
    }

    @Override
    public String getProviderName() { return "MOCK_FALLBACK"; }

    @Override
    public int getPriority() { return 99; }
}

