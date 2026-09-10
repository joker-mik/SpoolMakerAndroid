/* SPDX-License-Identifier: GPL-3.0-or-later */
package de.spoolmaker.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import de.spoolmaker.android.model.MaterialProfile;
import de.spoolmaker.android.nfc.NtagIo;
import de.spoolmaker.android.nfc.UltimakerTagCodec;
import de.spoolmaker.android.storage.MaterialStore;
import de.spoolmaker.android.util.CuraMaterialParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final int REQUEST_IMPORT_MATERIAL = 1401;
    private static final String EXTRA_REOPEN_LANGUAGE_PAGE = "de.spoolmaker.android.extra.REOPEN_LANGUAGE_PAGE";
    private static final AtomicBoolean NFC_IO_ACTIVE = new AtomicBoolean(false);

    private enum NfcState {
        IDLE,
        WAITING_READ,
        WAITING_WRITE,
        READING,
        WRITING
    }

    private enum StatusKind {
        INFO,
        SUCCESS,
        WARNING
    }

    private NfcAdapter nfcAdapter;
    private MaterialStore materialStore;
    private final CuraMaterialParser curaMaterialParser = new CuraMaterialParser();

    private Spinner spinnerMaterial;
    private EditText editTotalWeightGrams;
    private EditText editRemainingWeightGrams;
    private TextView textStatus;
    private TextView textScanEmpty;
    private TextView textUid;
    private TextView textGuid;
    private TextView textMaterialResult;
    private TextView textWeightResult;
    private TextView textTimestamp;
    private TextView textBatch;
    private TextView textStation;
    private TextView textCrc;
    private TextView textFullDetails;
    private TextView textDetailsEmpty;
    private TextView textRawDump;
    private TextView textRawDumpVisible;
    private TextView textRawEmpty;
    private Button buttonToggleRaw;
    private Button buttonCopySummary;
    private Button buttonCopyDetails;
    private Button buttonCopyRaw;
    private ImageButton buttonEdit;
    private ImageButton buttonDelete;
    private Button buttonRead;
    private Button buttonWrite;
    private Spinner spinnerWriteDateMeaning;
    private Button buttonWriteDate;
    private final Calendar selectedWriteDate = Calendar.getInstance();
    private final UltimakerTagCodec.DateMeaning[] writeDateMeanings = new UltimakerTagCodec.DateMeaning[]{
            UltimakerTagCodec.DateMeaning.OPENED,
            UltimakerTagCodec.DateMeaning.MANUFACTURED,
            UltimakerTagCodec.DateMeaning.PURCHASED,
            UltimakerTagCodec.DateMeaning.CREATED,
            UltimakerTagCodec.DateMeaning.NONE
    };

    private View readPage;
    private View writePage;
    private View detailsPanel;
    private View drawerOverlay;
    private View secondaryPage;
    private View materialPage;
    private View languagePage;
    private View licensePage;
    private View textPageScroll;
    private LinearLayout materialList;
    private View tabRead;
    private View tabWrite;
    private TextView tabReadLabel;
    private TextView tabWriteLabel;
    private ImageView tabReadIcon;
    private ImageView tabWriteIcon;
    private TextView textPageTitle;
    private TextView textPageBody;
    private android.widget.RadioButton radioLanguageSystem;
    private android.widget.RadioButton radioLanguageGerman;
    private android.widget.RadioButton radioLanguageEnglish;
    private AlertDialog nfcPrompt;
    private TextView nfcPromptMessage;
    private String selectedLibraryGuid;

    private ArrayAdapter<String> materialAdapter;
    private List<MaterialProfile> materials = new ArrayList<>();

    private final Object nfcStateLock = new Object();
    private volatile NfcState nfcState = NfcState.IDLE;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private volatile MaterialProfile pendingWriteMaterial;
    private volatile long pendingWriteTotalWeightMg;
    private volatile long pendingWriteRemainingWeightMg;
    private volatile UltimakerTagCodec.DateMeaning pendingWriteDateMeaning = UltimakerTagCodec.DateMeaning.NONE;
    private volatile long pendingWriteDateEpochSeconds;

    private String lastDetailsText = "";
    private String lastRawDumpText = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureSystemBars();
            setContentView(R.layout.activity_main);
            configureSystemBarInsets();

            materialStore = new MaterialStore(this);
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            bindViews();
            configureWriteDateUi();
            configureMaterialSpinner();
            configureActions();
            configureNavigationUi();
            refreshMaterials(null);
            if (getIntent().getBooleanExtra(EXTRA_REOPEN_LANGUAGE_PAGE, false)) {
                getIntent().removeExtra(EXTRA_REOPEN_LANGUAGE_PAGE);
                showLanguagePage();
            }
            registerModernBackHandler();

            if (nfcAdapter == null) {
                showUserMessage(StatusKind.WARNING, tr(
                        "This Android device does not have a compatible NFC adapter.",
                        "Dieses Android-Gerät besitzt keinen kompatiblen NFC-Adapter."));
            } else if (!nfcAdapter.isEnabled()) {
                showUserMessage(StatusKind.WARNING, tr(
                        "NFC is disabled. Enable NFC before reading or writing a tag.",
                        "NFC ist deaktiviert. Aktiviere NFC, bevor du einen Tag liest oder schreibst."));
            }
        } catch (RuntimeException startupError) {
            showStartupFailure(startupError);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        View decorView = window.getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decorView.setSystemUiVisibility(flags);
        }

        window.setStatusBarColor(getColor(R.color.primary));
        window.setNavigationBarColor(getColor(R.color.screen));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void configureSystemBarInsets() {
        View root = findViewById(R.id.rootInsetHost);
        View top = findViewById(R.id.topBar);
        View bottom = findViewById(R.id.bottomActionBar);
        View drawerHeader = findViewById(R.id.drawerHeader);
        View drawerPanel = findViewById(R.id.drawerPanel);
        View secondaryHeader = findViewById(R.id.secondaryHeader);
        View secondaryRoot = findViewById(R.id.secondaryPage);

        final int rootLeft = root.getPaddingLeft();
        final int rootRight = root.getPaddingRight();
        final int topLeft = top.getPaddingLeft();
        final int topTop = top.getPaddingTop();
        final int topRight = top.getPaddingRight();
        final int topBottom = top.getPaddingBottom();
        final int bottomLeft = bottom.getPaddingLeft();
        final int bottomTop = bottom.getPaddingTop();
        final int bottomRight = bottom.getPaddingRight();
        final int bottomBottom = bottom.getPaddingBottom();
        final int drawerLeft = drawerHeader.getPaddingLeft();
        final int drawerTop = drawerHeader.getPaddingTop();
        final int drawerRight = drawerHeader.getPaddingRight();
        final int drawerBottom = drawerHeader.getPaddingBottom();
        final int drawerPanelLeft = drawerPanel.getPaddingLeft();
        final int drawerPanelTop = drawerPanel.getPaddingTop();
        final int drawerPanelRight = drawerPanel.getPaddingRight();
        final int drawerPanelBottom = drawerPanel.getPaddingBottom();
        final int pageRootLeft = secondaryRoot.getPaddingLeft();
        final int pageRootTop = secondaryRoot.getPaddingTop();
        final int pageRootRight = secondaryRoot.getPaddingRight();
        final int pageRootBottom = secondaryRoot.getPaddingBottom();
        final int pageLeft = secondaryHeader.getPaddingLeft();
        final int pageTop = secondaryHeader.getPaddingTop();
        final int pageRight = secondaryHeader.getPaddingRight();
        final int pageBottom = secondaryHeader.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int topInset;
            int right;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets safeInsets = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safeInsets.left;
                topInset = safeInsets.top;
                right = safeInsets.right;
                bottomInset = safeInsets.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                topInset = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottomInset = insets.getSystemWindowInsetBottom();
            }

            root.setPadding(rootLeft + left, 0, rootRight + right, 0);
            top.setPadding(topLeft, topTop + topInset, topRight, topBottom);
            drawerHeader.setPadding(drawerLeft, drawerTop + topInset, drawerRight, drawerBottom);
            drawerPanel.setPadding(drawerPanelLeft, drawerPanelTop, drawerPanelRight, drawerPanelBottom + bottomInset);
            secondaryHeader.setPadding(pageLeft, pageTop + topInset, pageRight, pageBottom);
            secondaryRoot.setPadding(pageRootLeft, pageRootTop, pageRootRight, pageRootBottom + bottomInset);
            bottom.setPadding(bottomLeft, bottomTop, bottomRight, bottomBottom + bottomInset);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void showStartupFailure(RuntimeException startupError) {
        TextView message = new TextView(this);
        message.setPadding(32, 32, 32, 32);
        message.setTextSize(16);
        String detail = startupError.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = startupError.getClass().getSimpleName();
        }
        message.setText(tr(
                "Spool Maker could not be initialized completely.\n\n",
                "Spool Maker konnte nicht vollstaendig initialisiert werden.\n\n")
                + startupError.getClass().getSimpleName() + ": " + detail);
        setContentView(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableReaderMode();
        updateNfcActionButtons();
    }

    @Override
    protected void onPause() {
        if (isNfcWaiting()) {
            dismissNfcPrompt();
            cancelPendingAction(false);
        } else if (isNfcProcessing()) {
            // The platform may pause the Activity while a transceive is active. Do not
            // pretend to cancel an EEPROM write that may already be in progress.
            dismissNfcPrompt();
        }
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        importExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        final NfcState operation;
        final MaterialProfile writeMaterial;
        final long writeTotalWeightMg;
        final long writeRemainingWeightMg;
        final UltimakerTagCodec.DateMeaning writeDateMeaning;
        final long writeDateEpochSeconds;

        boolean globalIoAcquired = false;
        synchronized (nfcStateLock) {
            if (nfcState == NfcState.WAITING_READ) {
                operation = NfcState.READING;
            } else if (nfcState == NfcState.WAITING_WRITE) {
                operation = NfcState.WRITING;
            } else {
                return;
            }
            globalIoAcquired = NFC_IO_ACTIVE.compareAndSet(false, true);
            if (!globalIoAcquired) {
                nfcState = NfcState.IDLE;
                clearPendingWriteDataLocked();
                postUi(() -> {
                    dismissNfcPrompt();
                    updateNfcActionButtons();
                    showUserMessage(StatusKind.WARNING, tr(
                            "Another NFC operation is still running. Please try again afterwards.",
                            "Eine andere NFC-Kommunikation läuft noch. Bitte danach erneut versuchen."));
                });
                return;
            }
            nfcState = operation;
            writeMaterial = pendingWriteMaterial;
            writeTotalWeightMg = pendingWriteTotalWeightMg;
            writeRemainingWeightMg = pendingWriteRemainingWeightMg;
            writeDateMeaning = pendingWriteDateMeaning;
            writeDateEpochSeconds = pendingWriteDateEpochSeconds;
            clearPendingWriteDataLocked();
        }

        String uid = NtagIo.formatUid(tag);
        postUi(() -> updateNfcPromptForProcessing(operation == NfcState.WRITING));

        try {
            if (operation == NfcState.READING) {
                NtagIo.ReadResult readResult = NtagIo.readSupportedUserMemory(tag);
                byte[] memory = readResult.getUserMemory();
                UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(memory);
                String model = readResult.getTagInfo().getDisplayName();
                postUi(() -> {
                    dismissNfcPrompt();
                    showDecodedSpool(uid, decoded, memory);
                    showUserMessage(StatusKind.SUCCESS,
                            model + tr(" was read successfully.", " wurde erfolgreich gelesen."));
                    vibrateSuccess();
                });
                return;
            }

            if (writeMaterial == null) {
                throw new IllegalStateException(tr(
                        "No material is selected for writing.",
                        "Kein Material für den Schreibvorgang ausgewählt."));
            }

            byte[] encoded = UltimakerTagCodec.encodeSpool(
                    writeMaterial.getGuid(), uid,
                    writeTotalWeightMg, writeRemainingWeightMg,
                    writeDateMeaning, writeDateEpochSeconds);

            NtagIo.WriteResult writeResult = NtagIo.writeAndVerifyUserMemory(tag, encoded);
            byte[] verification = writeResult.getVerification();
            UltimakerTagCodec.DecodedSpool decoded = UltimakerTagCodec.decode(verification);

            if (!decoded.getMaterialGuid().equals(writeMaterial.getGuid())
                    || decoded.getTotalAmount() != writeTotalWeightMg
                    || decoded.getRemainingAmount() != writeRemainingWeightMg
                    || !UltimakerTagCodec.isIntegrityValid(uid, decoded)
                    || !decoded.isSpoolMakerTag()
                    || decoded.getDateMeaning() != writeDateMeaning
                    || (writeDateMeaning != UltimakerTagCodec.DateMeaning.NONE
                    && Math.round(decoded.getTimeFieldDoubleSeconds()) != writeDateEpochSeconds)) {
                throw new IOException(tr(
                        "The tag was fully written, but semantic content verification failed.",
                        "Der Tag wurde vollständig geschrieben, aber die semantische Inhaltsprüfung ist fehlgeschlagen."));
            }

            String model = writeResult.getTagInfo().getDisplayName();
            postUi(() -> {
                dismissNfcPrompt();
                showDecodedSpool(uid, decoded, verification);
                showUserMessage(StatusKind.SUCCESS, tr(
                        model + " was written and verified byte-for-byte across "
                                + verification.length + " bytes.",
                        model + " wurde geschrieben und über " + verification.length
                                + " Byte bytegenau verifiziert."));
                vibrateSuccess();
            });
        } catch (NtagIo.PartialWriteException exception) {
            String detail = safeExceptionMessage(exception);
            postUi(() -> {
                dismissNfcPrompt();
                showUserError(tr("NFC write error", "NFC-Schreibfehler"), detail);
            });
        } catch (Exception exception) {
            String detail = safeExceptionMessage(exception);
            postUi(() -> {
                dismissNfcPrompt();
                showUserError(tr("NFC error", "NFC-Fehler"), detail);
            });
        } finally {
            if (globalIoAcquired) {
                NFC_IO_ACTIVE.set(false);
            }
            synchronized (nfcStateLock) {
                nfcState = NfcState.IDLE;
                clearPendingWriteDataLocked();
            }
            postUi(this::updateNfcActionButtons);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_MATERIAL || resultCode != RESULT_OK || data == null) {
            return;
        }

        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (uris.isEmpty()) {
            showUserMessage(StatusKind.WARNING, tr(
                    "No material file selected.", "Keine Materialdatei ausgewählt."));
            return;
        }

        showUserMessage(StatusKind.INFO, uris.size() == 1
                ? tr("Material import in progress …", "Materialimport läuft …")
                : tr("Material import in progress (" + uris.size() + " files) …",
                "Materialimport läuft (" + uris.size() + " Dateien) …"));
        importExecutor.execute(() -> importMaterials(uris));
    }

    private void importMaterials(List<Uri> uris) {
        List<MaterialProfile> parsedProfiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Uri uri : uris) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    throw new IOException(tr(
                            "File could not be opened.", "Datei konnte nicht geöffnet werden."));
                }
                parsedProfiles.add(curaMaterialParser.parse(stream));
            } catch (IOException | XmlPullParserException | IllegalArgumentException exception) {
                errors.add(safeExceptionMessage(exception));
            }
        }

        if (!parsedProfiles.isEmpty()) {
            try {
                materialStore.upsertAll(parsedProfiles);
            } catch (RuntimeException exception) {
                errors.add(tr(
                        "Material library could not be saved: ",
                        "Materialbibliothek konnte nicht gespeichert werden: ")
                        + safeExceptionMessage(exception));
                parsedProfiles.clear();
            }
        }

        final int imported = parsedProfiles.size();
        final String lastGuid = imported == 0 ? null
                : parsedProfiles.get(imported - 1).getGuid();
        final String firstError = errors.isEmpty() ? null : errors.get(0);
        final int errorCount = errors.size();

        postUi(() -> {
            refreshMaterials(lastGuid);
            if (imported > 0 && errorCount == 0) {
                showUserMessage(StatusKind.SUCCESS, imported == 1
                        ? tr("1 material was imported.", "1 Material wurde importiert.")
                        : tr(imported + " materials were imported.",
                        imported + " Materialien wurden importiert."));
            } else if (imported > 0) {
                showUserMessage(StatusKind.WARNING, tr(
                        imported + " material file(s) imported; " + errorCount
                                + " file(s) could not be processed. First error: " + firstError,
                        imported + " Materialdatei(en) importiert; " + errorCount
                                + " Datei(en) konnten nicht verarbeitet werden. Erster Fehler: "
                                + firstError));
            } else {
                showUserError(tr("Import failed", "Import fehlgeschlagen"),
                        firstError == null
                                ? tr("No file could be imported.",
                                "Keine Datei konnte importiert werden.")
                                : firstError);
            }
        });
    }

    private void bindViews() {
        spinnerMaterial = findViewById(R.id.spinnerMaterial);
        editTotalWeightGrams = findViewById(R.id.editTotalWeightGrams);
        editRemainingWeightGrams = findViewById(R.id.editRemainingWeightGrams);
        textStatus = findViewById(R.id.textStatus);
        textScanEmpty = findViewById(R.id.textScanEmpty);
        textUid = findViewById(R.id.textUid);
        textGuid = findViewById(R.id.textGuid);
        textMaterialResult = findViewById(R.id.textMaterialResult);
        textWeightResult = findViewById(R.id.textWeightResult);
        textTimestamp = findViewById(R.id.textTimestamp);
        textBatch = findViewById(R.id.textBatch);
        textStation = findViewById(R.id.textStation);
        textCrc = findViewById(R.id.textCrc);
        textFullDetails = findViewById(R.id.textFullDetails);
        textDetailsEmpty = findViewById(R.id.textDetailsEmpty);
        textRawDump = findViewById(R.id.textRawDump);
        textRawDumpVisible = findViewById(R.id.textRawDumpVisible);
        textRawEmpty = findViewById(R.id.textRawEmpty);
        buttonToggleRaw = findViewById(R.id.buttonToggleRaw);
        buttonCopySummary = findViewById(R.id.buttonCopySummary);
        buttonCopyDetails = findViewById(R.id.buttonCopyDetails);
        buttonCopyRaw = findViewById(R.id.buttonCopyRaw);
        buttonEdit = findViewById(R.id.buttonEdit);
        buttonDelete = findViewById(R.id.buttonDelete);
        buttonRead = findViewById(R.id.buttonRead);
        buttonWrite = findViewById(R.id.buttonWrite);
        readPage = findViewById(R.id.readPage);
        writePage = findViewById(R.id.writePage);
        detailsPanel = findViewById(R.id.detailsPanel);
        drawerOverlay = findViewById(R.id.drawerOverlay);
        secondaryPage = findViewById(R.id.secondaryPage);
        materialPage = findViewById(R.id.materialPage);
        languagePage = findViewById(R.id.languagePage);
        licensePage = findViewById(R.id.licensePage);
        textPageScroll = findViewById(R.id.textPageScroll);
        materialList = findViewById(R.id.materialList);
        tabRead = findViewById(R.id.tabRead);
        tabWrite = findViewById(R.id.tabWrite);
        tabReadLabel = findViewById(R.id.tabReadLabel);
        tabWriteLabel = findViewById(R.id.tabWriteLabel);
        tabReadIcon = findViewById(R.id.tabReadIcon);
        tabWriteIcon = findViewById(R.id.tabWriteIcon);
        textPageTitle = findViewById(R.id.textPageTitle);
        textPageBody = findViewById(R.id.textPageBody);
        radioLanguageSystem = findViewById(R.id.radioLanguageSystem);
        radioLanguageGerman = findViewById(R.id.radioLanguageGerman);
        radioLanguageEnglish = findViewById(R.id.radioLanguageEnglish);
    }

    private void configureWriteDateUi() {
        if (!(editRemainingWeightGrams.getParent() instanceof ViewGroup)) {
            throw new IllegalStateException(tr(
                    "Write section does not have a suitable container for date fields.",
                    "Schreibbereich hat keinen geeigneten Container fuer Datumsfelder."));
        }

        ViewGroup parent = (ViewGroup) editRemainingWeightGrams.getParent();
        LinearLayout dateSection = new LinearLayout(this);
        dateSection.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = dp(12);
        sectionParams.bottomMargin = dp(4);

        TextView dateMeaningLabel = new TextView(this);
        dateMeaningLabel.setText(tr("Filament date", "Filament-Datum"));
        dateMeaningLabel.setTextColor(getColor(R.color.text_primary));
        dateMeaningLabel.setTextSize(16f);
        dateMeaningLabel.setTypeface(null, Typeface.BOLD);
        dateMeaningLabel.setPadding(0, 0, 0, dp(4));

        spinnerWriteDateMeaning = new Spinner(this);
        String[] labels = new String[]{
                tr("Opened on", "Geöffnet am"),
                tr("Manufacturing date", "Herstellungsdatum"),
                tr("Purchase date", "Kaufdatum"),
                tr("Spool created on", "Spule angelegt am"),
                tr("No date", "Kein Datum")
        };
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWriteDateMeaning.setAdapter(dateAdapter);
        spinnerWriteDateMeaning.setSelection(0);

        TextView dateLabel = new TextView(this);
        dateLabel.setText(tr("Date", "Datum"));
        dateLabel.setTextColor(getColor(R.color.text_primary));
        dateLabel.setTextSize(16f);
        dateLabel.setTypeface(null, Typeface.BOLD);
        dateLabel.setPadding(0, dp(10), 0, dp(4));

        buttonWriteDate = new Button(this);
        buttonWriteDate.setAllCaps(false);
        updateDateButton(buttonWriteDate, selectedWriteDate);
        buttonWriteDate.setOnClickListener(view -> new DatePickerDialog(
                this,
                (picker, year, month, day) -> {
                    selectedWriteDate.set(Calendar.YEAR, year);
                    selectedWriteDate.set(Calendar.MONTH, month);
                    selectedWriteDate.set(Calendar.DAY_OF_MONTH, day);
                    updateDateButton(buttonWriteDate, selectedWriteDate);
                },
                selectedWriteDate.get(Calendar.YEAR),
                selectedWriteDate.get(Calendar.MONTH),
                selectedWriteDate.get(Calendar.DAY_OF_MONTH)).show());

        spinnerWriteDateMeaning.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean hasDate = writeDateMeanings[position] != UltimakerTagCodec.DateMeaning.NONE;
                buttonWriteDate.setEnabled(hasDate);
                if (hasDate) {
                    updateDateButton(buttonWriteDate, selectedWriteDate);
                } else {
                    buttonWriteDate.setText(tr("No date", "Kein Datum"));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                buttonWriteDate.setEnabled(false);
                buttonWriteDate.setText(tr("No date", "Kein Datum"));
            }
        });

        dateSection.addView(dateMeaningLabel);
        dateSection.addView(spinnerWriteDateMeaning);
        dateSection.addView(dateLabel);
        dateSection.addView(buttonWriteDate);

        int insertIndex = parent.indexOfChild(editRemainingWeightGrams) + 1;
        parent.addView(dateSection, insertIndex, sectionParams);
    }

    private void configureMaterialSpinner() {
        materialAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, new ArrayList<>());
        materialAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaterial.setAdapter(materialAdapter);
        spinnerMaterial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applySelectedMaterialWeight();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the user's current entries if the selection temporarily disappears.
            }
        });
    }

    private void applySelectedMaterialWeight() {
        MaterialProfile profile = getSelectedMaterial();
        if (profile == null) {
            return;
        }
        long defaultWeightMg = profile.getSpoolWeightMg() > 0
                ? profile.getSpoolWeightMg()
                : 1_000_000L;
        String grams = formatWeightInput(defaultWeightMg);
        editTotalWeightGrams.setText(grams);
        editRemainingWeightGrams.setText(grams);
        selectedLibraryGuid = profile.getGuid();
        if (materialPage != null && materialPage.getVisibility() == View.VISIBLE) {
            renderMaterialLibrary();
        }
    }

    private void configureActions() {
        findViewById(R.id.buttonAdd).setOnClickListener(view -> showMaterialDialog(null));
        buttonEdit.setOnClickListener(view -> {
            MaterialProfile profile = getSelectedMaterial();
            if (profile != null) {
                showMaterialDialog(profile);
            }
        });
        buttonDelete.setOnClickListener(view -> confirmDeleteSelectedMaterial());
        findViewById(R.id.buttonImport).setOnClickListener(view -> launchMaterialImport());
        buttonRead.setOnClickListener(view -> armRead());
        buttonWrite.setOnClickListener(view -> confirmAndArmWrite());
        findViewById(R.id.buttonCancel).setOnClickListener(view -> cancelPendingAction(true));
        buttonToggleRaw.setOnClickListener(view -> toggleRawDump());
        buttonCopyDetails.setOnClickListener(view -> copyToClipboard(
                tr("UltiMaker tag data", "Ultimaker-Tagdaten"), lastDetailsText));
        buttonCopyRaw.setOnClickListener(view -> copyToClipboard(
                tr("UltiMaker raw data", "Ultimaker-Rohdaten"), lastRawDumpText));
        findViewById(R.id.buttonDetails).setOnClickListener(view -> {
            boolean show = detailsPanel.getVisibility() != View.VISIBLE;
            detailsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    private void configureNavigationUi() {
        findViewById(R.id.buttonMenu).setOnClickListener(view -> openDrawer());
        findViewById(R.id.buttonDrawerBack).setOnClickListener(view -> closeDrawer());
        findViewById(R.id.drawerOverlay).setOnClickListener(view -> closeDrawer());
        findViewById(R.id.menuMaterials).setOnClickListener(view -> {
            closeDrawer();
            showMaterialPage();
        });
        findViewById(R.id.menuLanguage).setOnClickListener(view -> {
            closeDrawer();
            showLanguagePage();
        });
        findViewById(R.id.menuInfo).setOnClickListener(view -> {
            closeDrawer();
            showInfoPage();
        });
        findViewById(R.id.menuLicense).setOnClickListener(view -> {
            closeDrawer();
            showLicensePage();
        });
        findViewById(R.id.buttonPageBack).setOnClickListener(view -> closeSecondaryPage());
        radioLanguageSystem.setOnClickListener(view -> selectLanguage(LocaleHelper.LANGUAGE_SYSTEM));
        radioLanguageGerman.setOnClickListener(view -> selectLanguage(LocaleHelper.LANGUAGE_GERMAN));
        radioLanguageEnglish.setOnClickListener(view -> selectLanguage(LocaleHelper.LANGUAGE_ENGLISH));
        findViewById(R.id.buttonLicenseFull).setOnClickListener(view -> showLicenseDialog());
        configureDrawerAppearance();
        tabRead.setOnClickListener(view -> selectTab(true));
        tabWrite.setOnClickListener(view -> selectTab(false));
        selectTab(true);
    }

    private void configureDrawerAppearance() {
        View materialsEntry = findViewById(R.id.menuMaterials);
        if (materialsEntry instanceof TextView) {
            TextView materialsText = (TextView) materialsEntry;
            materialsText.setText(R.string.page_materials);
            materialsText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_database, 0, 0, 0);
        } else {
            TextView materialsText = findFirstTextView(materialsEntry);
            if (materialsText != null) {
                materialsText.setText(R.string.page_materials);
            }
            ImageView materialsIcon = findFirstImageView(materialsEntry);
            if (materialsIcon != null) {
                materialsIcon.setImageResource(R.drawable.ic_database);
            }
        }

        TextView version = findViewById(R.id.textDrawerVersion);
        if (version != null) {
            version.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));
            version.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
            version.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            android.view.ViewGroup.LayoutParams params = version.getLayoutParams();
            if (params != null) {
                params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                version.setLayoutParams(params);
            }
        }
    }

    private TextView findFirstTextView(View root) {
        if (root == null) {
            return null;
        }
        if (root instanceof TextView) {
            return (TextView) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView result = findFirstTextView(group.getChildAt(index));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private ImageView findFirstImageView(View root) {
        if (root == null) {
            return null;
        }
        if (root instanceof ImageView) {
            return (ImageView) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                ImageView result = findFirstImageView(group.getChildAt(index));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private void selectTab(boolean read) {
        readPage.setVisibility(read ? View.VISIBLE : View.GONE);
        writePage.setVisibility(read ? View.GONE : View.VISIBLE);
        buttonRead.setVisibility(read ? View.VISIBLE : View.GONE);
        buttonWrite.setVisibility(read ? View.GONE : View.VISIBLE);
        tabRead.setBackgroundResource(read ? R.drawable.bg_tab_selected : android.R.color.transparent);
        tabWrite.setBackgroundResource(read ? android.R.color.transparent : R.drawable.bg_tab_selected);
        tabReadLabel.setTypeface(null, read ? Typeface.BOLD : Typeface.NORMAL);
        tabWriteLabel.setTypeface(null, read ? Typeface.NORMAL : Typeface.BOLD);
        int readColor = getColor(read ? R.color.text_primary : R.color.text_secondary);
        int writeColor = getColor(read ? R.color.text_secondary : R.color.text_primary);
        tabReadLabel.setTextColor(readColor);
        tabWriteLabel.setTextColor(writeColor);
        tabReadIcon.setColorFilter(readColor);
        tabWriteIcon.setColorFilter(writeColor);
    }

    private void openDrawer() {
        drawerOverlay.setVisibility(View.VISIBLE);
    }

    private void closeDrawer() {
        drawerOverlay.setVisibility(View.GONE);
    }

    private void closeSecondaryPage() {
        secondaryPage.setVisibility(View.GONE);
    }

    private void showLanguagePage() {
        textPageTitle.setText(R.string.language_title);
        materialPage.setVisibility(View.GONE);
        licensePage.setVisibility(View.GONE);
        textPageScroll.setVisibility(View.GONE);
        languagePage.setVisibility(View.VISIBLE);
        updateLanguageSelection();
        secondaryPage.setVisibility(View.VISIBLE);
    }

    private void updateLanguageSelection() {
        int checkedItem = LocaleHelper.choiceIndex(LocaleHelper.getLanguage(this));
        radioLanguageSystem.setChecked(checkedItem == 0);
        radioLanguageGerman.setChecked(checkedItem == 1);
        radioLanguageEnglish.setChecked(checkedItem == 2);
    }

    private void selectLanguage(String selectedLanguage) {
        boolean changed = !selectedLanguage.equals(LocaleHelper.getLanguage(this));
        LocaleHelper.setLanguage(this, selectedLanguage);
        updateLanguageSelection();
        if (changed) {
            getIntent().putExtra(EXTRA_REOPEN_LANGUAGE_PAGE, true);
            recreate();
        }
    }

    private void showMaterialPage() {
        textPageTitle.setText(R.string.page_materials);
        materialPage.setVisibility(View.VISIBLE);
        languagePage.setVisibility(View.GONE);
        licensePage.setVisibility(View.GONE);
        textPageScroll.setVisibility(View.GONE);
        renderMaterialLibrary();
        secondaryPage.setVisibility(View.VISIBLE);
    }

    private void showInfoPage() {
        textPageTitle.setText(R.string.page_info);
        materialPage.setVisibility(View.GONE);
        languagePage.setVisibility(View.GONE);
        licensePage.setVisibility(View.GONE);
        textPageScroll.setVisibility(View.VISIBLE);
        textPageBody.setTypeface(android.graphics.Typeface.DEFAULT);
        textPageBody.setText(getString(R.string.info_body, BuildConfig.VERSION_NAME));
        Linkify.addLinks(textPageBody, Linkify.WEB_URLS);
        textPageBody.setMovementMethod(LinkMovementMethod.getInstance());
        secondaryPage.setVisibility(View.VISIBLE);
    }

    private void showLicensePage() {
        textPageTitle.setText(R.string.page_license);
        materialPage.setVisibility(View.GONE);
        languagePage.setVisibility(View.GONE);
        textPageScroll.setVisibility(View.GONE);
        licensePage.setVisibility(View.VISIBLE);

        TextView version = findViewById(R.id.textLicenseVersion);
        version.setText(getString(R.string.license_app_version, BuildConfig.VERSION_NAME));

        TextView upstream = findViewById(R.id.textLicenseUpstream);
        Linkify.addLinks(upstream, Linkify.WEB_URLS);
        upstream.setMovementMethod(LinkMovementMethod.getInstance());

        TextView source = findViewById(R.id.textLicenseSource);
        Linkify.addLinks(source, Linkify.WEB_URLS);
        source.setMovementMethod(LinkMovementMethod.getInstance());

        secondaryPage.setVisibility(View.VISIBLE);
    }

    private void renderMaterialLibrary() {
        if (materialList == null) {
            return;
        }
        materialList.removeAllViews();
        if (materials.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.materials_empty);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(16f);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            materialList.addView(empty);
            return;
        }

        for (int index = 0; index < materials.size(); index++) {
            MaterialProfile profile = materials.get(index);
            TextView row = new TextView(this);
            long weight = profile.getSpoolWeightMg();
            String weightText = weight > 0
                    ? formatWeightInput(weight) + " g"
                    : tr("not stored", "nicht gespeichert");
            row.setText(profile.getDisplayName()
                    + tr("\nSpool weight: ", "\nSpulengewicht: ") + weightText);
            row.setTextSize(17f);
            row.setTextColor(getColor(R.color.text_primary));
            row.setMinHeight(dp(72));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(profile.getGuid().equals(selectedLibraryGuid)
                    ? R.drawable.bg_material_selected : R.drawable.bg_material_row);
            int position = index;
            row.setOnClickListener(view -> {
                selectedLibraryGuid = profile.getGuid();
                spinnerMaterial.setSelection(position);
                renderMaterialLibrary();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            materialList.addView(row, params);
        }
    }

    private int dp(int value) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private void showNfcPrompt(boolean write) {
        dismissNfcPrompt();

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(16), dp(24), dp(8));

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.ic_nfc);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        body.addView(icon, iconParams);

        nfcPromptMessage = new TextView(this);
        nfcPromptMessage.setText(write
                ? tr("Hold a writable NTAG215 or NTAG216 near the NFC antenna.",
                "Beschreibbaren NTAG215 oder NTAG216 an die NFC-Antenne halten.")
                : tr("Hold an NTAG215 or NTAG216 near the NFC antenna.",
                "NTAG215 oder NTAG216 an die NFC-Antenne halten."));
        nfcPromptMessage.setTextSize(19f);
        nfcPromptMessage.setTextColor(getColor(R.color.text_primary));
        nfcPromptMessage.setGravity(android.view.Gravity.CENTER);
        nfcPromptMessage.setPadding(0, dp(18), 0, dp(6));
        body.addView(nfcPromptMessage);

        nfcPrompt = new AlertDialog.Builder(this)
                .setTitle(write
                        ? tr("Write NFC tag", "NFC-Tag schreiben")
                        : tr("Read NFC tag", "NFC-Tag lesen"))
                .setView(body)
                .setNegativeButton(R.string.action_cancel,
                        (dialog, which) -> cancelPendingAction(false))
                .create();
        nfcPrompt.setCanceledOnTouchOutside(false);
        nfcPrompt.setOnCancelListener(dialog -> cancelPendingAction(false));
        nfcPrompt.show();
    }

    private void updateNfcPromptForProcessing(boolean write) {
        if (nfcPrompt == null || !nfcPrompt.isShowing()) {
            return;
        }
        nfcPrompt.setTitle(write
                ? tr("Writing NFC tag", "NFC-Tag wird geschrieben")
                : tr("Reading NFC tag", "NFC-Tag wird gelesen"));
        if (nfcPromptMessage != null) {
            nfcPromptMessage.setText(write
                    ? tr("Tag detected. Type and write protection are checked before writing and verification. Do not remove the tag.",
                    "Tag erkannt. Typ und Schreibschutz werden geprüft, danach wird geschrieben und verifiziert. Tag nicht entfernen.")
                    : tr("Tag detected. Type and memory are checked and read. Do not remove the tag.",
                    "Tag erkannt. Typ und Speicher werden geprüft und gelesen. Tag nicht entfernen."));
        }
        nfcPrompt.setCancelable(false);
        Button negative = nfcPrompt.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setEnabled(false);
            negative.setVisibility(View.GONE);
        }
    }

    private void dismissNfcPrompt() {
        if (nfcPrompt != null) {
            nfcPrompt.dismiss();
            nfcPrompt = null;
            nfcPromptMessage = null;
        }
    }

    private boolean handleBackNavigation() {
        if (nfcPrompt != null && nfcPrompt.isShowing()) {
            if (isNfcProcessing()) {
                return true;
            }
            dismissNfcPrompt();
            cancelPendingAction(false);
            return true;
        }
        if (drawerOverlay != null && drawerOverlay.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return true;
        }
        if (secondaryPage != null && secondaryPage.getVisibility() == View.VISIBLE) {
            closeSecondaryPage();
            return true;
        }
        if (detailsPanel != null && detailsPanel.getVisibility() == View.VISIBLE) {
            detailsPanel.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (!handleBackNavigation()) {
            super.onBackPressed();
        }
    }

    private void registerModernBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        if (!handleBackNavigation()) {
                            finish();
                        }
                    });
        }
    }

    private void refreshMaterials(String selectedGuid) {
        materials = materialStore.getAll();
        materialAdapter.clear();
        if (materials.isEmpty()) {
            materialAdapter.add(tr("No materials stored", "Keine Materialien gespeichert"));
        } else {
            for (MaterialProfile profile : materials) {
                materialAdapter.add(profile.getDisplayName());
            }
        }
        materialAdapter.notifyDataSetChanged();

        boolean hasMaterials = !materials.isEmpty();
        spinnerMaterial.setEnabled(hasMaterials);
        buttonEdit.setEnabled(hasMaterials);
        buttonDelete.setEnabled(hasMaterials);
        updateNfcActionButtons();

        if (hasMaterials && selectedGuid != null) {
            for (int index = 0; index < materials.size(); index++) {
                if (materials.get(index).getGuid().equals(selectedGuid)) {
                    spinnerMaterial.setSelection(index);
                    selectedLibraryGuid = selectedGuid;
                    break;
                }
            }
        }
        if (hasMaterials) {
            boolean selectedExists = false;
            if (selectedLibraryGuid != null) {
                for (MaterialProfile profile : materials) {
                    if (profile.getGuid().equals(selectedLibraryGuid)) {
                        selectedExists = true;
                        break;
                    }
                }
            }
            if (!selectedExists) {
                selectedLibraryGuid = materials.get(0).getGuid();
                spinnerMaterial.setSelection(0);
            }
        } else {
            selectedLibraryGuid = null;
        }
        renderMaterialLibrary();
    }

    private MaterialProfile getSelectedMaterial() {
        int position = spinnerMaterial.getSelectedItemPosition();
        if (position < 0 || position >= materials.size()) {
            return null;
        }
        return materials.get(position);
    }

    private void showMaterialDialog(MaterialProfile existing) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_material, null, false);
        EditText editBrand = content.findViewById(R.id.editBrand);
        EditText editMaterial = content.findViewById(R.id.editMaterial);
        EditText editColor = content.findViewById(R.id.editColor);
        EditText editGuid = content.findViewById(R.id.editGuid);
        EditText editSpoolWeight = content.findViewById(R.id.editSpoolWeightGrams);

        if (existing != null) {
            editBrand.setText(existing.getBrand());
            editMaterial.setText(existing.getMaterial());
            editColor.setText(existing.getColor());
            editGuid.setText(existing.getGuid());
            if (existing.getSpoolWeightMg() > 0) {
                editSpoolWeight.setText(formatWeightInput(existing.getSpoolWeightMg()));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null
                        ? tr("Add material", "Material hinzufügen")
                        : tr("Edit material", "Material bearbeiten"))
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(tr("Save", "Speichern"), null)
                .create();

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    try {
                        long spoolWeightMg = parseOptionalWeightMg(editSpoolWeight.getText().toString());
                        MaterialProfile profile = new MaterialProfile(
                                editBrand.getText().toString(),
                                editMaterial.getText().toString(),
                                editColor.getText().toString(),
                                editGuid.getText().toString(),
                                spoolWeightMg);
                        if (existing != null) {
                            materialStore.replace(existing.getGuid(), profile);
                        } else {
                            materialStore.upsert(profile);
                        }
                        refreshMaterials(profile.getGuid());
                        showUserMessage(StatusKind.SUCCESS, tr(
                                "Material saved: " + profile.getDisplayName(),
                                "Material gespeichert: " + profile.getDisplayName()));
                        dialog.dismiss();
                    } catch (IllegalArgumentException exception) {
                        String message = exception.getMessage();
                        String lower = message == null ? "" : message.toLowerCase(Locale.US);
                        if (lower.contains("gewicht") || lower.contains("weight")) {
                            editSpoolWeight.setError(LocaleHelper.isGerman(this)
                                    ? message : "Invalid spool weight.");
                        } else {
                            editGuid.setError(LocaleHelper.isGerman(this)
                                    ? message : "Invalid material data or GUID.");
                        }
                    } catch (RuntimeException exception) {
                        showUserError(tr("Save failed", "Speichern fehlgeschlagen"),
                                safeExceptionMessage(exception));
                    }
                });
        });
        dialog.show();
    }

    private void confirmDeleteSelectedMaterial() {
        MaterialProfile profile = getSelectedMaterial();
        if (profile == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(tr("Delete material?", "Material löschen?"))
                .setMessage(profile.getDisplayName() + "\n" + profile.getGuid())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(tr("Delete", "Löschen"), (dialog, which) -> {
                    try {
                        materialStore.remove(profile.getGuid());
                        refreshMaterials(null);
                        showUserMessage(StatusKind.SUCCESS, tr(
                                "Material was removed from the local library.",
                                "Material wurde aus der lokalen Bibliothek gelöscht."));
                    } catch (RuntimeException exception) {
                        showUserError(tr("Delete failed", "Löschen fehlgeschlagen"),
                                safeExceptionMessage(exception));
                    }
                })
                .show();
    }

    private void launchMaterialImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/xml", "text/xml", "application/octet-stream", "text/plain"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_IMPORT_MATERIAL);
    }

    private void armRead() {
        if (!ensureNfcReady()) {
            return;
        }
        synchronized (nfcStateLock) {
            if (nfcState != NfcState.IDLE) {
                showUserMessage(StatusKind.WARNING, tr("An NFC action is already running.", "Es läuft bereits eine NFC-Aktion."));
                return;
            }
            clearPendingWriteDataLocked();
            nfcState = NfcState.WAITING_READ;
        }
        updateNfcActionButtons();
        setInternalStatus(tr("Read mode is active.", "Lesen ist aktiviert."));
        showNfcPrompt(false);
    }

    private void confirmAndArmWrite() {
        if (!ensureNfcReady()) {
            return;
        }
        MaterialProfile profile = getSelectedMaterial();
        if (profile == null) {
            showUserMessage(StatusKind.WARNING, tr(
                    "A material must be added or imported before writing.",
                    "Vor dem Schreiben muss ein Material angelegt oder importiert werden."));
            return;
        }

        long totalWeightMg;
        long remainingWeightMg;
        try {
            totalWeightMg = parseWeightMg(editTotalWeightGrams.getText().toString(), false);
        } catch (IllegalArgumentException exception) {
            editTotalWeightGrams.setError(exception.getMessage());
            return;
        }
        try {
            remainingWeightMg = parseWeightMg(editRemainingWeightGrams.getText().toString(), true);
            if (remainingWeightMg > totalWeightMg) {
                throw new IllegalArgumentException(tr(
                        "Remaining material must not exceed the total amount.",
                        "Restmaterial darf nicht groesser als die Gesamtmenge sein."));
            }
        } catch (IllegalArgumentException exception) {
            editRemainingWeightGrams.setError(exception.getMessage());
            return;
        }

        showWriteConfirmation(profile, totalWeightMg, remainingWeightMg);
    }

    private void showWriteConfirmation(MaterialProfile profile, long totalWeightMg,
                                       long remainingWeightMg) {
        int datePosition = spinnerWriteDateMeaning == null
                ? writeDateMeanings.length - 1
                : spinnerWriteDateMeaning.getSelectedItemPosition();
        if (datePosition < 0 || datePosition >= writeDateMeanings.length) {
            datePosition = writeDateMeanings.length - 1;
        }

        UltimakerTagCodec.DateMeaning meaning = writeDateMeanings[datePosition];
        long dateEpochSeconds = meaning == UltimakerTagCodec.DateMeaning.NONE
                ? 0L : toUtcDateEpochSeconds(selectedWriteDate);
        String dateSummary = meaning == UltimakerTagCodec.DateMeaning.NONE
                ? tr("No custom date", "Kein eigenes Datum")
                : writeDateMeaningLabel(meaning) + ": "
                + DateFormat.getDateInstance(DateFormat.MEDIUM).format(selectedWriteDate.getTime());

        String message = profile.getDisplayName() + "\n"
                + profile.getGuid() + "\n\n"
                + tr("Total amount: ", "Gesamtmenge: ") + formatWeight(totalWeightMg) + "\n"
                + tr("Remaining material: ", "Restmaterial: ") + formatWeight(remainingWeightMg) + "\n"
                + tr("Filament date: ", "Filament-Datum: ") + dateSummary + "\n\n"
                + tr(
                "The existing spool content will be overwritten. NTAG215 and NTAG216 are supported. "
                        + "Tag type, lock bits and password protection are checked before the first byte is written.",
                "Der vorhandene Spuleninhalt wird überschrieben. Zugelassen sind NTAG215 und NTAG216. "
                        + "Tag-Typ, Lock-Bits und Passwortschutz werden vor dem ersten Schreibbyte geprüft.");

        new AlertDialog.Builder(this)
                .setTitle(tr("Write NFC tag?", "NFC-Tag schreiben?"))
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(tr("Enable writing", "Schreiben aktivieren"),
                        (dialog, which) -> {
                    synchronized (nfcStateLock) {
                        if (nfcState != NfcState.IDLE) {
                            showUserMessage(StatusKind.WARNING, tr(
                                    "An NFC action is already running.",
                                    "Es läuft bereits eine NFC-Aktion."));
                            return;
                        }
                        pendingWriteMaterial = profile;
                        pendingWriteTotalWeightMg = totalWeightMg;
                        pendingWriteRemainingWeightMg = remainingWeightMg;
                        pendingWriteDateMeaning = meaning;
                        pendingWriteDateEpochSeconds = dateEpochSeconds;
                        nfcState = NfcState.WAITING_WRITE;
                    }
                    updateNfcActionButtons();
                    setInternalStatus(tr("Write mode is active.", "Schreiben ist aktiviert."));
                    showNfcPrompt(true);
                })
                .show();
    }

    private String writeDateMeaningLabel(UltimakerTagCodec.DateMeaning meaning) {
        switch (meaning) {
            case MANUFACTURED:
                return tr("Manufacturing date", "Herstellungsdatum");
            case PURCHASED:
                return tr("Purchase date", "Kaufdatum");
            case OPENED:
                return tr("Opened on", "Geöffnet am");
            case CREATED:
                return tr("Spool created on", "Spule angelegt am");
            case NONE:
            default:
                return tr("No date", "Kein Datum");
        }
    }

    private void updateDateButton(Button button, Calendar date) {
        button.setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.getTime()));
    }

    private long toUtcDateEpochSeconds(Calendar selectedDate) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return utc.getTimeInMillis() / 1000L;
    }

    private boolean ensureNfcReady() {
        if (NFC_IO_ACTIVE.get()) {
            showUserMessage(StatusKind.WARNING, tr(
                    "An NFC operation is still running. Please try again afterwards.",
                    "Eine NFC-Kommunikation läuft noch. Bitte danach erneut versuchen."));
            return false;
        }
        if (isNfcProcessing() || isNfcWaiting()) {
            showUserMessage(StatusKind.WARNING, tr("An NFC action is already running.", "Es läuft bereits eine NFC-Aktion."));
            return false;
        }
        if (nfcAdapter == null) {
            showUserMessage(StatusKind.WARNING, tr(
                    "This device does not have a compatible NFC adapter.",
                    "Dieses Gerät besitzt keinen kompatiblen NFC-Adapter."));
            return false;
        }
        if (!nfcAdapter.isEnabled()) {
            setInternalStatus(tr("NFC is disabled.", "NFC ist deaktiviert."));
            new AlertDialog.Builder(this)
                    .setTitle(tr("Enable NFC", "NFC aktivieren"))
                    .setMessage(tr(
                            "Enable NFC in Android settings and then return to the app.",
                            "Aktiviere NFC in den Android-Einstellungen und kehre danach zur App zurück."))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(tr("Settings", "Einstellungen"),
                            (dialog, which) -> openNfcSettings())
                    .show();
            return false;
        }
        return true;
    }

    private void openNfcSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } catch (android.content.ActivityNotFoundException ignored) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    private void enableReaderMode() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) {
            return;
        }
        Bundle options = new Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
        nfcAdapter.enableReaderMode(this, this, flags, options);
    }

    private void cancelPendingAction(boolean updateStatus) {
        synchronized (nfcStateLock) {
            if (nfcState == NfcState.READING || nfcState == NfcState.WRITING) {
                if (updateStatus) {
                    showUserMessage(StatusKind.WARNING, tr(
                            "The active NFC operation cannot be cancelled safely.",
                            "Die laufende NFC-Kommunikation kann nicht sicher abgebrochen werden."));
                }
                return;
            }
            nfcState = NfcState.IDLE;
            clearPendingWriteDataLocked();
        }
        dismissNfcPrompt();
        updateNfcActionButtons();
        if (updateStatus) {
            showUserMessage(StatusKind.INFO, tr(
                    "NFC action was cancelled.", "NFC-Aktion wurde abgebrochen."));
        }
    }

    private void clearPendingWriteDataLocked() {
        pendingWriteMaterial = null;
        pendingWriteTotalWeightMg = 0;
        pendingWriteRemainingWeightMg = 0;
        pendingWriteDateMeaning = UltimakerTagCodec.DateMeaning.NONE;
        pendingWriteDateEpochSeconds = 0;
    }

    private boolean isNfcWaiting() {
        NfcState state = nfcState;
        return state == NfcState.WAITING_READ || state == NfcState.WAITING_WRITE;
    }

    private boolean isNfcProcessing() {
        NfcState state = nfcState;
        return state == NfcState.READING || state == NfcState.WRITING;
    }

    private void updateNfcActionButtons() {
        if (buttonRead == null || buttonWrite == null) {
            return;
        }
        boolean idle = nfcState == NfcState.IDLE;
        boolean nfcReady = nfcAdapter != null && nfcAdapter.isEnabled();
        buttonRead.setEnabled(idle && nfcReady);
        buttonWrite.setEnabled(idle && nfcReady && !materials.isEmpty());
    }

    private long parseWeightMg(String raw, boolean allowZero) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(tr(
                    "Enter a weight in grams.", "Bitte ein Gewicht in Gramm eingeben."));
        }
        try {
            BigDecimal grams = new BigDecimal(raw.trim().replace(',', '.'));
            BigDecimal milligrams = grams.multiply(BigDecimal.valueOf(1000L))
                    .setScale(0, RoundingMode.HALF_UP);
            long value = milligrams.longValueExact();
            if (value < 0 || (!allowZero && value == 0) || value > UltimakerTagCodec.MAX_UNSIGNED_INT) {
                throw new IllegalArgumentException(tr(
                        "Weight is outside the tag format range.",
                        "Gewicht liegt ausserhalb des Tagformats."));
            }
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(tr(
                    "Invalid weight.", "Ungueltiges Gewicht."), exception);
        }
    }

    private long parseOptionalWeightMg(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0L;
        }
        return parseWeightMg(raw, false);
    }

    private String formatWeightInput(long milligrams) {
        return localizeDecimal(BigDecimal.valueOf(milligrams, 3)
                .stripTrailingZeros()
                .toPlainString());
    }

    private void showDecodedSpool(String uid, UltimakerTagCodec.DecodedSpool decoded,
                                  byte[] memory) {
        textScanEmpty.setVisibility(View.GONE);
        MaterialProfile known = materialStore.findByGuid(decoded.getMaterialGuid());
        String materialName = known == null
                ? tr("Not in the local library (name/color are not stored on the tag)",
                "Nicht in der lokalen Bibliothek (Name/Farbe sind nicht auf dem Tag gespeichert)")
                : known.getDisplayName()
                + tr(" (local match via GUID)", " (lokale Zuordnung ueber GUID)");

        textUid.setText(getString(R.string.label_uid) + ": " + uid
                + (decoded.getSerial().isEmpty() ? "" : " (Materialrecord: " + decoded.getSerial() + ")"));
        textGuid.setText(getString(R.string.label_guid) + ": " + decoded.getMaterialGuid());
        textMaterialResult.setText(getString(R.string.label_material) + ": " + materialName);

        textWeightResult.setText(getString(R.string.label_weight)
                + tr(": Total ", ": Gesamt ")
                + formatAmount(decoded.getTotalAmount(), decoded.getUnit())
                + tr(", remaining ", ", verbleibend ")
                + formatAmount(decoded.getRemainingAmount(), decoded.getUnit()));

        textTimestamp.setText(getString(R.string.label_timestamp) + ": "
                + formatMaterialDate(decoded)
                + formatCustomDateAgeSuffix(decoded)
                + tr(", usage duration ", ", Nutzungsdauer ")
                + formatDuration(decoded.getTotalUsageDurationSecondsUnsigned()));
        textBatch.setText(getString(R.string.label_batch) + ": " + decoded.getBatchCode());
        textStation.setText(getString(R.string.label_station) + ": 0x"
                + String.format(Locale.US, "%04X", decoded.getStationId())
                + " (" + decoded.getStationId() + ")");

        boolean uidMatches = UltimakerTagCodec.uidMatchesSerial(uid, decoded.getSerial());
        boolean expectedLayout = UltimakerTagCodec.hasExpectedNdefLayout(decoded);
        boolean integrityOk = UltimakerTagCodec.isIntegrityValid(uid, decoded);
        String integrity = "CRC-8 "
                + (decoded.isStatusCrcValid() ? tr("valid", "gueltig") : tr("INVALID", "UNGUELTIG"))
                + tr(", active status ", ", aktiv Status ") + decoded.getActiveStatusRecordIndex()
                + ", UID/Serial " + (uidMatches ? "OK" : tr("MISMATCH", "ABWEICHEND"))
                + tr(", material records ", ", Materialrecords ") + decoded.getMaterialRecordCount()
                + tr(", signature records ", ", Signaturrecords ") + decoded.getSignatureRecordCount()
                + tr(", status records ", ", Statusrecords ") + decoded.getStatusRecordCount()
                + (decoded.isDuplicateStatusMatches()
                ? tr(" (byte-identical)", " (bytegleich)")
                : tr(" (different, normally possible)", " (unterschiedlich, normal moeglich)"))
                + ", Layout " + (expectedLayout ? "OK" : tr("MISMATCH", "ABWEICHEND"))
                + tr(", signature marker ", ", Sig-Marker ")
                + (decoded.hasExpectedSigMarker() ? "0x2000" : tr("missing/mismatch", "fehlt/abweichend"));
        textCrc.setText(getString(R.string.label_crc) + ": " + integrity);
        textCrc.setTextColor(getColor(integrityOk ? R.color.accent_dark : R.color.danger));

        lastDetailsText = buildFullDetails(uid, decoded);
        lastRawDumpText = buildRawDump(memory);
        textFullDetails.setText(lastDetailsText);
        textDetailsEmpty.setVisibility(View.GONE);
        textFullDetails.setVisibility(View.VISIBLE);

        textRawDump.setText(lastRawDumpText);
        textRawDump.setVisibility(View.GONE);
        textRawDumpVisible.setText(lastRawDumpText);
        textRawEmpty.setVisibility(View.GONE);
        textRawDumpVisible.setVisibility(View.VISIBLE);

        buttonToggleRaw.setText(R.string.button_show_raw);
        buttonCopySummary.setEnabled(true);
        buttonCopyDetails.setEnabled(true);
        buttonCopyRaw.setEnabled(true);
        buttonToggleRaw.setEnabled(true);
    }

    private String buildFullDetails(String uid, UltimakerTagCodec.DecodedSpool decoded) {
        StringBuilder out = new StringBuilder(4096);
        appendHeading(out, tr("TAG AND NDEF", "TAG UND NDEF"));
        appendValue(out, "Chip-UID", uid);
        appendValue(out, tr("Material record serial field", "Materialrecord-Serienfeld"), emptyAsMarker(decoded.getSerial()));
        appendValue(out, tr("Data range passed to decoder", "An Decoder uebergebener Datenbereich"), decoded.getReadMemoryLength() + " Byte");
        appendValue(out, tr("Data range", "Datenbereich"), tr("NTAG page 4 to ", "NTAG-Seite 4 bis ")
                + (NtagIo.FIRST_USER_PAGE + decoded.getReadMemoryLength() / 4 - 1));
        appendValue(out, tr("NDEF storage", "NDEF-Ablage"), decoded.isTlvWrapped()
                ? "NFC-Forum-Type-2-TLV" : tr("raw NDEF byte stream from page 4", "roher NDEF-Bytestrom ab Seite 4"));
        appendValue(out, "NDEF offset", decoded.getNdefOffset() + tr(" bytes from user memory", " Byte ab Benutzerspeicher"));
        appendValue(out, tr("NDEF length", "NDEF-Laenge"), decoded.getNdefLength() + " Byte");
        appendValue(out, "NDEF-Records", Integer.toString(decoded.getNdefRecords().size()));
        appendValue(out, tr("Material records", "Materialrecords"), Integer.toString(decoded.getMaterialRecordCount()));
        appendValue(out, tr("Signature records", "Signaturrecords"), Integer.toString(decoded.getSignatureRecordCount()));
        appendValue(out, tr("Status records", "Statusrecords"), Integer.toString(decoded.getStatusRecordCount()));

        appendHeading(out, tr("MATERIAL RECORD", "MATERIALRECORD"));
        appendValue(out, tr("Format version", "Formatversion"), Integer.toString(decoded.getMaterialVersion()));
        appendValue(out, tr("Compatibility version", "Kompatibilitaetsversion"), Integer.toString(decoded.getMaterialCompatibility()));
        appendValue(out, tr("Serial number (14-byte field)", "Seriennummer (14-Byte-Feld)"), emptyAsMarker(decoded.getSerial()));
        appendValue(out, tr("Raw time field (hex)", "Zeitfeld roh (Hex)"), decoded.getTimeFieldRawHex());
        appendValue(out, tr("Raw time field (uint64)", "Zeitfeld roh (uint64)"), decoded.getTimeFieldUnsigned().toString());
        appendValue(out, tr("Time field as BE IEEE-754 double", "Zeitfeld als BE IEEE-754 double"),
                formatDoubleSeconds(decoded.getTimeFieldDoubleSeconds()));
        appendValue(out, tr("Interpreted time field", "Zeitfeld interpretiert"), formatMaterialDate(decoded));
        appendValue(out, tr("SpoolMaker tag format", "SpoolMaker-Tagformat"), yesNo(decoded.isSpoolMakerTag()));
        appendValue(out, tr("SpoolMaker date type", "SpoolMaker-Datumsart"), decoded.isSpoolMakerTag()
                ? dateMeaningLabel(decoded.getDateMeaning()) : tr("Original tag / not set", "Originaltag / nicht gesetzt"));
        if (decoded.hasSpoolMakerDate()) {
            appendValue(out, tr("Age since date", "Alter seit Datum"), formatCustomDateAge(decoded));
        }
        appendValue(out, "Material-GUID", decoded.getMaterialGuid());
        appendValue(out, tr("Programming station ID", "Programmierstations-ID"), "0x"
                + String.format(Locale.US, "%04X", decoded.getStationId())
                + " (" + decoded.getStationId() + ")");
        appendValue(out, tr("Batch code (64-byte field)", "Batchcode (64-Byte-Feld)"), emptyAsMarker(decoded.getBatchCode()));
        appendValue(out, "Trailing/unknown Bytes [106..107]", decoded.getMaterialTrailingHex());

        appendHeading(out, tr("SIGNATURE RECORD", "SIGNATURRECORD"));
        appendValue(out, tr("Present", "Vorhanden"), yesNo(decoded.isSignaturePresent()));
        appendValue(out, "Payload", emptyAsMarker(decoded.getSignaturePayloadHex()));
        appendValue(out, tr("Value", "Wert"), decoded.getSignatureValue() < 0
                ? tr("not readable as a 16-bit value", "nicht als 16-Bit-Wert lesbar")
                : "0x" + String.format(Locale.US, "%04X", decoded.getSignatureValue())
                + " (" + decoded.getSignatureValue() + ")");
        appendValue(out, tr("Signature marker equals 0x2000", "Sig-Marker entspricht 0x2000"), yesNo(decoded.hasExpectedSigMarker()));

        for (UltimakerTagCodec.DecodedStatusRecord status : decoded.getStatusRecords()) {
            appendHeading(out, "STATUSRECORD " + status.getIndex()
                    + (status.getIndex() == decoded.getActiveStatusRecordIndex() ? tr(" (ACTIVE)", " (AKTIV)") : ""));
            appendValue(out, tr("Format version", "Formatversion"), Integer.toString(status.getVersion()));
            appendValue(out, tr("Compatibility version", "Kompatibilitaetsversion"), Integer.toString(status.getCompatibility()));
            appendValue(out, tr("Unit", "Einheit"), status.getUnit() + " ("
                    + localizedUnitLabel(status.getUnit()) + ")");
            appendValue(out, tr("Raw total amount", "Gesamtmenge roh"), Long.toString(status.getTotalAmount()));
            appendValue(out, tr("Formatted total amount", "Gesamtmenge formatiert"),
                    formatAmount(status.getTotalAmount(), status.getUnit()));
            appendValue(out, tr("Raw remaining amount", "Verbleibende Menge roh"), Long.toString(status.getRemainingAmount()));
            appendValue(out, tr("Formatted remaining amount", "Verbleibende Menge formatiert"),
                    formatAmount(status.getRemainingAmount(), status.getUnit()));
            appendValue(out, tr("Consumed (calculated)", "Verbraucht (berechnet)"),
                    formatAmount(status.getTotalAmount() - status.getRemainingAmount(), status.getUnit()));
            appendValue(out, tr("Remaining share (calculated)", "Restanteil (berechnet)"),
                    formatRemainingPercentage(status.getRemainingAmount(), status.getTotalAmount()));
            appendValue(out, tr("Raw usage duration", "Nutzungsdauer roh"), status.getTotalUsageDurationSecondsUnsigned() + " s");
            appendValue(out, tr("Formatted usage duration", "Nutzungsdauer formatiert"),
                    formatDuration(status.getTotalUsageDurationSecondsUnsigned()));
            appendValue(out, tr("Stored CRC", "CRC gespeichert"), "0x"
                    + String.format(Locale.US, "%02X", status.getStoredCrc()));
            appendValue(out, tr("Calculated CRC", "CRC berechnet"), "0x"
                    + String.format(Locale.US, "%02X", status.getCalculatedCrc()));
            appendValue(out, tr("CRC valid", "CRC gueltig"), yesNo(status.isCrcValid()));
            appendValue(out, "Payload (20 Byte)", status.getPayloadHex());
        }

        appendHeading(out, tr("CONSISTENCY", "KONSISTENZ"));
        appendValue(out, tr("All status CRCs valid", "Alle Status-CRC gueltig"), yesNo(decoded.isStatusCrcValid()));
        appendValue(out, tr("Active status record", "Aktiver Statusrecord"), Integer.toString(decoded.getActiveStatusRecordIndex()));
        appendValue(out, tr("Status records byte-identical (information only)", "Statusrecords bytegleich (nur Information)"), yesNo(decoded.isDuplicateStatusMatches()));
        appendValue(out, tr("UID matches serial field", "UID entspricht Serienfeld"),
                yesNo(UltimakerTagCodec.uidMatchesSerial(uid, decoded.getSerial())));
        appendValue(out, tr("Signature marker 0x2000 present", "Sig-Marker 0x2000 vorhanden"), yesNo(decoded.hasExpectedSigMarker()));
        appendValue(out, tr("Expected four-record NDEF layout", "Erwartetes Vier-Record-NDEF-Layout"),
                yesNo(UltimakerTagCodec.hasExpectedNdefLayout(decoded)));
        appendValue(out, tr("Overall integrity", "Gesamtintegritaet"),
                yesNo(UltimakerTagCodec.isIntegrityValid(uid, decoded)));

        appendHeading(out, tr("ALL NDEF RECORDS", "ALLE NDEF-RECORDS"));
        for (UltimakerTagCodec.DecodedNdefRecord record : decoded.getNdefRecords()) {
            out.append("Record ").append(record.getIndex()).append('\n');
            appendValue(out, tr("  Offset/length", "  Offset/Laenge"), record.getOffset() + " / "
                    + record.getRecordLength() + " Byte");
            appendValue(out, tr("  Header flags", "  Headerflags"), "0x"
                    + String.format(Locale.US, "%02X", record.getFlags())
                    + " [MB=" + bit(record.isMessageBegin())
                    + ", ME=" + bit(record.isMessageEnd())
                    + ", SR=" + bit(record.isShortRecord())
                    + ", IL=" + bit(record.hasId()) + "]");
            appendValue(out, "  TNF", record.getTnf() + " (" + tnfLabel(record.getTnf()) + ")");
            appendValue(out, tr("  Type", "  Typ"), emptyAsMarker(record.getType()));
            appendValue(out, "  ID Text", emptyAsMarker(record.getIdText()));
            appendValue(out, "  ID Hex", emptyAsMarker(record.getIdHex()));
            appendValue(out, tr("  Payload length", "  Payload-Laenge"), record.getPayloadLength() + " Byte");
            appendValue(out, "  Payload Hex", emptyAsMarker(record.getPayloadHex()));
            out.append('\n');
        }
        return out.toString().trim();
    }

    private String buildRawDump(byte[] memory) {
        StringBuilder out = new StringBuilder(memory.length * 5);
        out.append(tr("Read NFC data range\n", "Gelesener NFC-Datenbereich\n"))
                .append(tr("Pages 4 to ", "Seiten 4 bis "))
                .append(NtagIo.FIRST_USER_PAGE + memory.length / 4 - 1)
                .append(", ").append(memory.length).append(" Byte\n\n");
        for (int offset = 0; offset < memory.length; offset += 4) {
            int page = NtagIo.FIRST_USER_PAGE + offset / 4;
            out.append(String.format(Locale.US, "P%03d  +%04X  ", page, offset));
            StringBuilder ascii = new StringBuilder(4);
            for (int index = 0; index < 4; index++) {
                int position = offset + index;
                if (position < memory.length) {
                    int value = memory[position] & 0xFF;
                    out.append(String.format(Locale.US, "%02X ", value));
                    ascii.append(value >= 0x20 && value <= 0x7E ? (char) value : '.');
                } else {
                    out.append("   ");
                    ascii.append(' ');
                }
            }
            out.append(" ").append(ascii).append('\n');
        }
        return out.toString().trim();
    }

    private void appendHeading(StringBuilder out, String title) {
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append(title).append('\n');
        for (int index = 0; index < title.length(); index++) {
            out.append('-');
        }
        out.append('\n');
    }

    private void appendValue(StringBuilder out, String label, String value) {
        out.append(label).append(": ").append(value).append('\n');
    }

    private String emptyAsMarker(String value) {
        return value == null || value.isEmpty() ? tr("<empty>", "<leer>") : value;
    }

    private String yesNo(boolean value) {
        return value ? tr("yes", "ja") : tr("no", "nein");
    }

    private int bit(boolean value) {
        return value ? 1 : 0;
    }

    private String tnfLabel(int tnf) {
        switch (tnf) {
            case 0: return tr("empty", "leer");
            case 1: return "NFC Well Known";
            case 2: return "MIME";
            case 3: return "absolute URI";
            case 4: return "External Type";
            case 5: return tr("unknown", "unbekannt");
            case 6: return "unchanged";
            default: return tr("reserved", "reserviert");
        }
    }

    private String localizedUnitLabel(int unit) {
        switch (unit) {
            case UltimakerTagCodec.UNIT_UNUSED:
                return tr("unused", "nicht verwendet");
            case UltimakerTagCodec.UNIT_MILLIMETRES:
                return "mm";
            case UltimakerTagCodec.UNIT_MILLIGRAMS:
                return "mg";
            case UltimakerTagCodec.UNIT_CUBIC_CENTIMETRES:
                return "cm3";
            default:
                return tr("unknown", "unbekannt");
        }
    }

    private String formatAmount(long amount, int unit) {
        if (unit == UltimakerTagCodec.UNIT_MILLIGRAMS) {
            return formatWeight(amount);
        }
        if (unit == UltimakerTagCodec.UNIT_MILLIMETRES) {
            return amount + " mm";
        }
        if (unit == UltimakerTagCodec.UNIT_CUBIC_CENTIMETRES) {
            return amount + " cm3";
        }
        return amount + tr(" (unit code ", " (Einheitencode ") + unit + ")";
    }

    private String formatRemainingPercentage(long remaining, long total) {
        if (total == 0) {
            return tr("not calculable (total amount 0)",
                    "nicht berechenbar (Gesamtmenge 0)");
        }
        BigDecimal percent = BigDecimal.valueOf(remaining)
                .multiply(BigDecimal.valueOf(100L))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return localizeDecimal(percent.toPlainString()) + " %";
    }

    private void toggleRawDump() {
        if (lastRawDumpText.isEmpty()) {
            showUserMessage(StatusKind.WARNING, tr(
                    "No raw data available yet. Read a tag first.",
                    "Noch keine Rohdaten vorhanden. Zuerst einen Tag lesen."));
            return;
        }
        boolean show = textRawDump.getVisibility() != View.VISIBLE;
        textRawDump.setVisibility(show ? View.VISIBLE : View.GONE);
        buttonToggleRaw.setText(show ? R.string.button_hide_raw : R.string.button_show_raw);
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            showUserMessage(StatusKind.WARNING, tr(
                    "No data is available to copy yet.",
                    "Noch keine Daten zum Kopieren vorhanden."));
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showUserMessage(StatusKind.WARNING, tr(
                    "The clipboard is not available on this device.",
                    "Zwischenablage ist auf diesem Gerät nicht verfügbar."));
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        showUserMessage(StatusKind.SUCCESS, tr(
                label + " copied to the clipboard.",
                label + " wurden in die Zwischenablage kopiert."));
    }

    private String formatWeight(long milligrams) {
        String grams = localizeDecimal(BigDecimal.valueOf(milligrams, 3)
                .stripTrailingZeros()
                .toPlainString());
        return grams + " g (" + milligrams + " mg)";
    }

    private String formatMaterialDate(UltimakerTagCodec.DecodedSpool decoded) {
        double seconds = decoded.getTimeFieldDoubleSeconds();
        if (decoded.isSpoolMakerTag()) {
            if (!decoded.hasSpoolMakerDate()) {
                return tr("SpoolMaker: no date stored",
                        "SpoolMaker: kein Datum gespeichert");
            }
            return dateMeaningLabel(decoded.getDateMeaning()) + ": "
                    + formatEpochSeconds(seconds, true);
        }
        return tr("UltiMaker time field: ", "UltiMaker-Zeitfeld: ")
                + formatEpochSeconds(seconds, false)
                + tr(" (interpreted as BE double / Unix seconds)",
                " (als BE-double/Unix-Sekunden interpretiert)");
    }

    private String formatEpochSeconds(double seconds, boolean dateOnly) {
        if (!Double.isFinite(seconds)) {
            return tr("not interpretable as a finite IEEE-754 number",
                    "nicht als endliche IEEE-754-Zahl interpretierbar");
        }
        double millis = seconds * 1000.0d;
        if (!Double.isFinite(millis) || millis > Long.MAX_VALUE || millis < Long.MIN_VALUE) {
            return formatDoubleSeconds(seconds)
                    + tr(" (outside the Android date range)",
                    " (ausserhalb des Android-Datumsbereichs)");
        }
        Date date = new Date(Math.round(millis));
        DateFormat formatter = dateOnly
                ? DateFormat.getDateInstance(DateFormat.MEDIUM)
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        if (dateOnly) {
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return formatter.format(date) + " (Unix-double " + formatDoubleSeconds(seconds) + ")";
    }

    private String formatDoubleSeconds(double seconds) {
        if (!Double.isFinite(seconds)) {
            return Double.toString(seconds);
        }
        return BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString() + " s";
    }

    private String dateMeaningLabel(UltimakerTagCodec.DateMeaning meaning) {
        if (meaning == null) {
            return tr("Unknown date", "Unbekanntes Datum");
        }
        switch (meaning) {
            case MANUFACTURED: return tr("Manufacturing date", "Herstellungsdatum");
            case PURCHASED: return tr("Purchase date", "Kaufdatum");
            case OPENED: return tr("Opened on", "Geoeffnet am");
            case CREATED: return tr("Spool created on", "Spule angelegt am");
            default: return tr("No custom date", "Kein eigenes Datum");
        }
    }

    private String formatCustomDateAgeSuffix(UltimakerTagCodec.DecodedSpool decoded) {
        if (!decoded.hasSpoolMakerDate()) {
            return "";
        }
        return tr(", age since date: ", ", Alter seit Datum: ")
                + formatCustomDateAge(decoded);
    }

    private String formatCustomDateAge(UltimakerTagCodec.DecodedSpool decoded) {
        double seconds = decoded.getTimeFieldDoubleSeconds();
        if (!Double.isFinite(seconds)) {
            return tr("not calculable", "nicht berechenbar");
        }
        double millisDouble = seconds * 1000.0d;
        if (!Double.isFinite(millisDouble) || millisDouble > Long.MAX_VALUE
                || millisDouble < Long.MIN_VALUE) {
            return tr("not calculable", "nicht berechenbar");
        }
        Calendar localToday = Calendar.getInstance();
        Calendar utcToday = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcToday.clear();
        utcToday.set(localToday.get(Calendar.YEAR),
                localToday.get(Calendar.MONTH),
                localToday.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        long deltaMillis = utcToday.getTimeInMillis() - Math.round(millisDouble);
        if (deltaMillis < 0) {
            return tr("Date is in the future", "Datum liegt in der Zukunft");
        }
        long days = deltaMillis / 86_400_000L;
        return days + (days == 1
                ? tr(" day", " Tag")
                : tr(" days", " Tage"));
    }

    private String formatDuration(BigInteger seconds) {
        if (seconds.bitLength() > 63) {
            return seconds + tr(" s (too large for time decomposition)",
                    " s (zu gross fuer Zeitzerlegung)");
        }
        long value = seconds.longValue();
        long hours = value / 3600L;
        long minutes = (value % 3600L) / 60L;
        long remainingSeconds = value % 60L;
        return hours + " h " + minutes + " min " + remainingSeconds + " s"
                + " (" + value + " s)";
    }

    private String tr(String english, String german) {
        return LocaleHelper.isGerman(this) ? german : english;
    }

    private String localizeDecimal(String value) {
        if (value == null) {
            return "";
        }
        return LocaleHelper.isGerman(this) ? value.replace('.', ',') : value;
    }

    private void setInternalStatus(String message) {
        if (textStatus != null) {
            textStatus.setText(message == null ? "" : message);
        }
    }

    private void showUserMessage(StatusKind kind, String message) {
        setInternalStatus(message);
        if (!isUiUsable() || message == null || message.trim().isEmpty()) {
            return;
        }
        int duration = kind == StatusKind.INFO ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        Toast.makeText(this, message, duration).show();
    }

    private void showUserError(String title, String message) {
        setInternalStatus(message);
        if (!isUiUsable()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message == null || message.trim().isEmpty()
                        ? tr("Unknown error.", "Unbekannter Fehler.") : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void postUi(Runnable runnable) {
        runOnUiThread(() -> {
            if (isUiUsable()) {
                runnable.run();
            }
        });
    }

    private boolean isUiUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private String safeExceptionMessage(Throwable exception) {
        if (exception == null) {
            return tr("Unknown error.", "Unbekannter Fehler.");
        }
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.trim();
    }

    private void vibrateSuccess() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(80);
    }

    private void showAboutDialog() {
        String message = getString(R.string.info_body, BuildConfig.VERSION_NAME);
        new AlertDialog.Builder(this)
                .setTitle("Info")
                .setMessage(message)
                .setNegativeButton(tr("Close", "Schließen"), null)
                .setNeutralButton(tr("Show GPL-3.0", "GPL-3.0 anzeigen"),
                        (dialog, which) -> showLicenseDialog())
                .show();
    }

    private void showLicenseDialog() {
        String license;
        try {
            license = readRawText(R.raw.gpl_3);
        } catch (IOException exception) {
            license = tr("License text could not be loaded: ",
                    "Lizenztext konnte nicht geladen werden: ") + exception.getMessage();
        }

        TextView textView = new TextView(this);
        textView.setText(license);
        textView.setTextIsSelectable(true);
        textView.setTextSize(12);
        textView.setPadding(24, 16, 24, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("GNU General Public License v3")
                .setView(scrollView)
                .setPositiveButton(tr("Close", "Schließen"), null)
                .show();
    }

    private String readRawText(int resourceId) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getResources().openRawResource(resourceId), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
