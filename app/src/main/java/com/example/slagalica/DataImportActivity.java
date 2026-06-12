package com.example.slagalica;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.models.KoZnaZnaQuestion;
import com.example.slagalica.models.SpojnicaModel;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Arrays;

public class DataImportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Pokrećemo uvoz podataka
        importData();
    }

    private void importData() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Dodavanje "Ko zna zna" pitanja sa fiksnim ID-evima (q0, q1...) da se ne bi duplirali
        KoZnaZnaQuestion[] questions = {
            new KoZnaZnaQuestion("Koji je glavni grad Francuske?", Arrays.asList("London", "Pariz", "Berlin", "Rim"), 1),
            new KoZnaZnaQuestion("Koja planeta je poznata kao Crvena planeta?", Arrays.asList("Venera", "Mars", "Jupiter", "Saturn"), 1),
            new KoZnaZnaQuestion("Koliko kontinenata postoji na Zemlji?", Arrays.asList("5", "6", "7", "8"), 2),
            new KoZnaZnaQuestion("Ko je napisao 'Na Drini ćuprija'?", Arrays.asList("Miloš Crnjanski", "Bora Stanković", "Ivo Andrić", "Meša Selimović"), 2),
            new KoZnaZnaQuestion("Koji je hemijski simbol za zlato?", Arrays.asList("Ag", "Fe", "Au", "Pb"), 2),
            new KoZnaZnaQuestion("Najveći okean na svetu je?", Arrays.asList("Atlantski", "Indijski", "Severni ledeni", "Tihi"), 3)
        };

        for (int i = 0; i < questions.length; i++) {
            db.collection("ko_zna_zna_questions").document("q" + i).set(questions[i]);
        }

        // 2. Dodavanje "Spojnica" sa fiksnim ID-evima (s1a, s1b...)
        
        // Set 1 - Glavni gradovi (Varijanta A)
        SpojnicaModel s1a = new SpojnicaModel(
            "Glavni gradovi Evrope",
            Arrays.asList("Srbija", "Francuska", "Italija", "Nemačka", "Španija"),
            Arrays.asList("Madrid", "Berlin", "Beograd", "Rim", "Pariz"),
            Arrays.asList(2, 4, 3, 1, 0)
        );

        // Set 1 - Glavni gradovi (Varijanta B)
        SpojnicaModel s1b = new SpojnicaModel(
            "Glavni gradovi Evrope",
            Arrays.asList("Grčka", "Austrija", "Mađarska", "Poljska", "Češka"),
            Arrays.asList("Beč", "Varšava", "Atina", "Prag", "Budimpešta"),
            Arrays.asList(2, 0, 4, 1, 3)
        );

        // Set 2 - Sportisti (Varijanta A)
        SpojnicaModel s2a = new SpojnicaModel(
            "Poznati sportisti",
            Arrays.asList("Novak Đoković", "Nikola Jokić", "Ivana Španović", "Bogdan Bogdanović", "Dušan Tadić"),
            Arrays.asList("Atletika", "Fudbal", "Tenis", "Košarka (NBA)", "Košarka (Atlanta)"),
            Arrays.asList(2, 3, 0, 4, 1)
        );

        // Set 2 - Sportisti (Varijanta B)
        SpojnicaModel s2b = new SpojnicaModel(
            "Poznati sportisti",
            Arrays.asList("Lionel Mesi", "Rodžer Federer", "Majkl Džordan", "Usein Bolt", "Tajger Vuds"),
            Arrays.asList("Golf", "Atletika", "Tenis", "Fudbal", "Košarka"),
            Arrays.asList(3, 2, 4, 1, 0)
        );

        db.collection("spojnice").document("s1a").set(s1a);
        db.collection("spojnice").document("s1b").set(s1b);
        db.collection("spojnice").document("s2a").set(s2a);
        db.collection("spojnice").document("s2b").set(s2b);

        Toast.makeText(this, "Podaci su uspešno uvezeni u Firebase!", Toast.LENGTH_LONG).show();
        finish();
    }
}
