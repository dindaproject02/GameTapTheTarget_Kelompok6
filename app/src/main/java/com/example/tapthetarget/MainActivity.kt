package com.example.tapthetarget

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class MainActivity : AppCompatActivity() {

    // ==== data permainan (dasar) ====
    private var skor = 0
    private val durasiPermainan = 30_000L
    private var sisaWaktuDetik = 30
    private var timer: CountDownTimer? = null
    private var sedangDijeda = false
    private val acak = Random()

    // ==== Opsional 1: tingkat kesulitan ====
    private var jumlahKlik = 0
    private var ukuranTargetDp = 72
    private val ukuranMinimumDp = 40
    private var poinPerKlik = 10

    // ==== Opsional 2: target menghilang otomatis ====
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var tugasSembunyikan: Runnable? = null
    private val batasWaktuTarget = 1_500L

    // ==== Opsional 3: efek suara ====
    private lateinit var toneGenerator: ToneGenerator

    // ==== Opsional 4 & Save State: Konstanta
    companion object {
        private const val PREFS_NAME = "TapTheTargetPrefs"
        private const val KEY_SKOR_TERTINGGI = "KEY_SKOR_TERTINGGI"
        private const val KEY_SKOR = "KEY_SKOR"
        private const val KEY_WAKTU = "KEY_WAKTU"
    }

    private lateinit var tvSkorTertinggi: TextView

    // ==== Opsional 6: pola latar acak ====
    private val daftarLatar = listOf(
        R.drawable.bg_game,
        R.drawable.bg_game_biru,
        R.drawable.bg_game_ungu
    )

    // ==== view (dasar) ====
    private lateinit var frameGame: FrameLayout
    private lateinit var imgBackground: ImageView
    private lateinit var imgTarget: ImageView
    private lateinit var tvSkor: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var btnPause: Button
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var tvSkorJeda: TextView
    private lateinit var tvSkorAkhir: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) ambil referensi semua view
        frameGame = findViewById(R.id.frameGame)
        imgBackground = findViewById(R.id.imgBackground)
        imgTarget = findViewById(R.id.imgTarget)
        tvSkor = findViewById(R.id.tvSkor)
        tvWaktu = findViewById(R.id.tvWaktu)
        btnPause = findViewById(R.id.btnPause)
        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        tvSkorJeda = findViewById(R.id.tvSkorJeda)
        tvSkorAkhir = findViewById(R.id.tvSkorAkhir)
        tvSkorTertinggi = findViewById(R.id.tvSkorTertinggi)

        // 2) pasang event listener
        imgTarget.setOnClickListener { targetDitekan() }
        btnPause.setOnClickListener { jedaPermainan() }

        // Opsional 3: siapkan generator nada (volume 80 dari 100)
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

        findViewById<Button>(R.id.btnLanjutkan).setOnClickListener { lanjutkanPermainan() }
        findViewById<Button>(R.id.btnUlangi).setOnClickListener { mulaiPermainan() }
        findViewById<Button>(R.id.btnMainLagi).setOnClickListener { mulaiPermainan() }

        // 3) Penanganan state restore / mulai baru
        if (savedInstanceState != null) {
            skor = savedInstanceState.getInt(KEY_SKOR, 0)
            sisaWaktuDetik = savedInstanceState.getInt(KEY_WAKTU, 30)
            tvSkor.text = getString(R.string.label_skor, skor)
            tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            frameGame.post { pindahkanTarget() }
            jalankanTimer(sisaWaktuDetik * 1000L)
        } else {
            mulaiPermainan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel() // hentikan timer agar tidak bocor
        tugasSembunyikan?.let { handler.removeCallbacks(it) }
        toneGenerator.release() // wajib dilepas agar tidak memory leak
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SKOR, skor)
        outState.putInt(KEY_WAKTU, sisaWaktuDetik)
    }

    // ================= memulai / mengulang permainan =================
    private fun mulaiPermainan() {
        skor = 0
        sisaWaktuDetik = 30
        sedangDijeda = false

        // Opsional 1: reset kesulitan ke kondisi awal
        jumlahKlik = 0
        ukuranTargetDp = 72
        poinPerKlik = 10
        perbesarKecilkanTarget()

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)

        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        overlayGameOver.alpha = 1f

        imgTarget.visibility = View.VISIBLE
        imgTarget.isEnabled = true
        imgTarget.scaleX = 1f
        imgTarget.scaleY = 1f
        btnPause.isEnabled = true

        // Opsional 6: pilih latar belakang secara acak setiap ronde
        imgBackground.setImageResource(daftarLatar.random())

        // taruh target di posisi acak pertama
        frameGame.post { pindahkanTarget() }

        jalankanTimer(durasiPermainan)
    }

    // ================= posisi acak target =================
    private fun pindahkanTarget() {
        val lebarArea = frameGame.width
        val tinggiArea = frameGame.height
        if (lebarArea == 0 || tinggiArea == 0) return

        val jarakAman = 24
        val maxX = lebarArea - imgTarget.width - (jarakAman * 2)
        val maxY = tinggiArea - imgTarget.height - (jarakAman * 2)
        if (maxX <= 0 || maxY <= 0) return

        imgTarget.x = (jarakAman + acak.nextInt(maxX)).toFloat()
        imgTarget.y = (jarakAman + acak.nextInt(maxY)).toFloat()

        jadwalkanSembunyikanTarget() // Opsional 2: mulai hitung mundur baru
    }

    // ================= klik target =================
    private fun targetDitekan() {
        if (sedangDijeda) return

        // Opsional 3: bunyikan nada pendek sebagai umpan balik audio
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        tugasSembunyikan?.let { handler.removeCallbacks(it) } // batalkan jadwal sembunyi lama

        jumlahKlik++

        // Opsional 1a: poin per klik
        poinPerKlik = 20
        skor += poinPerKlik
        tvSkor.text = getString(R.string.label_skor, skor)

        // Tampilkan teks melayang saat klik berhasil
        tampilkanPoinMelayang()

        // Opsional 1b: setiap kelipatan 5 klik, target mengecil 6dp
        if (jumlahKlik % 5 == 0 && ukuranTargetDp > ukuranMinimumDp) {
            ukuranTargetDp = (ukuranTargetDp - 6).coerceAtLeast(ukuranMinimumDp)
            perbesarKecilkanTarget()
        }

        imgTarget.animate()
            .scaleX(0.6f).scaleY(0.6f).setDuration(70)
            .withEndAction {
                imgTarget.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }
            .start()

        pindahkanTarget()
    }

    /** Mengubah ukuran layoutParams target secara nyata */
    private fun perbesarKecilkanTarget() {
        val ukuranPx = dpKePx(ukuranTargetDp)
        val params = imgTarget.layoutParams
        params.width = ukuranPx
        params.height = ukuranPx
        imgTarget.layoutParams = params
    }

    // ================= timer =================
    private fun jalankanTimer(durasi: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durasi, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = (millisUntilFinished / 1000).toInt()
                tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            }

            override fun onFinish() {
                sisaWaktuDetik = 0
                tvWaktu.text = getString(R.string.label_waktu, 0)
                gameOver()
            }
        }.start()
    }

    // ================= jeda & lanjut =================
    private fun jedaPermainan() {
        if (sedangDijeda || overlayGameOver.visibility == View.VISIBLE) return

        sedangDijeda = true
        timer?.cancel()
        tugasSembunyikan?.let { handler.removeCallbacks(it) } // hentikan siklus sembunyi saat dijeda
        imgTarget.isEnabled = false

        tvSkorJeda.text = skor.toString()
        overlayPause.alpha = 0f
        overlayPause.visibility = View.VISIBLE
        overlayPause.animate().alpha(1f).setDuration(200).start()
    }

    private fun lanjutkanPermainan() {
        if (!sedangDijeda) return

        sedangDijeda = false
        overlayPause.visibility = View.GONE
        imgTarget.isEnabled = true

        // jalankan timer dari sisa waktu terakhir
        jalankanTimer(sisaWaktuDetik * 1000L)
    }

    // ================= game over =================
    private fun gameOver() {
        timer?.cancel()
        tugasSembunyikan?.let { handler.removeCallbacks(it) }
        sedangDijeda = false

        imgTarget.isEnabled = false
        imgTarget.visibility = View.GONE
        btnPause.isEnabled = false
        overlayPause.visibility = View.GONE

        tvSkorAkhir.text = skor.toString()

        // Opsional 4: simpan rekor baru lalu tampilkan
        simpanSkorJikaRekorBaru(skor)
        tvSkorTertinggi.text = "Rekor: ${ambilSkorTertinggi()}"

        overlayGameOver.alpha = 0f
        overlayGameOver.visibility = View.VISIBLE
        overlayGameOver.animate().alpha(1f).setDuration(350).start()
    }

    // ================= teks poin melayang =================
    private fun tampilkanPoinMelayang() {
        val label = TextView(this).apply {
            text = "+$poinPerKlik"
            setTextColor(0xFF86EFAC.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        frameGame.addView(label)

        label.x = imgTarget.x + 20f
        label.y = imgTarget.y - 30f

        label.animate()
            .alpha(0f)
            .translationYBy(-60f)
            .setDuration(600)
            .withEndAction { frameGame.removeView(label) }
            .start()
    }

    /** Mengonversi nilai dp menjadi piksel sesuai kepadatan layar */
    private fun dpKePx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /** Menjadwalkan target agar otomatis sembunyi & muncul lagi jika tidak ditekan */
    private fun jadwalkanSembunyikanTarget() {
        tugasSembunyikan?.let { handler.removeCallbacks(it) }

        tugasSembunyikan = Runnable {
            if (sedangDijeda || overlayGameOver.visibility == View.VISIBLE) return@Runnable

            imgTarget.visibility = View.INVISIBLE
            handler.postDelayed({
                if (!sedangDijeda && overlayGameOver.visibility != View.VISIBLE) {
                    pindahkanTarget()
                    imgTarget.visibility = View.VISIBLE
                    jadwalkanSembunyikanTarget()
                }
            }, 400L)
        }
        handler.postDelayed(tugasSembunyikan!!, batasWaktuTarget)
    }

    /** Mengambil skor tertinggi yang tersimpan */
    private fun ambilSkorTertinggi(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getInt(KEY_SKOR_TERTINGGI, 0)
    }

    /** Menyimpan skor baru bila lebih besar dari rekor sebelumnya */
    private fun simpanSkorJikaRekorBaru(skorBaru: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val rekorLama = prefs.getInt(KEY_SKOR_TERTINGGI, 0)
        if (skorBaru > rekorLama) {
            prefs.edit().putInt(KEY_SKOR_TERTINGGI, skorBaru).apply()
        }
    }
}