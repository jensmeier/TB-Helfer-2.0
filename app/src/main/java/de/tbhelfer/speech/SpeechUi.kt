package de.tbhelfer.speech

import android.content.Intent
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.tbhelfer.R
import de.tbhelfer.ServiceLocator
import de.tbhelfer.streckenobjekte.BaustellenObjektLogik
import de.tbhelfer.ui.bautagebuch.BtbActiveContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A view-lifecycle observer, no polling and no business mutation while counting drafts. */
object SpeechUi {
    suspend fun confirmPending(fragment: Fragment, btbId: String?): Boolean {
        if (btbId == null) return true
        val count = withContext(Dispatchers.IO) { SpeechDraftStore.list(fragment.requireContext(),btbId).size }
        if (count == 0) return true
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Noch $count Spracheingaben offen")
                .setMessage("Die lokalen Entwürfe sind noch nicht im BTB/PDF. Jetzt zurückgehen und prüfen oder ohne Übernahme beenden? Die Entwürfe bleiben erhalten.")
                .setNegativeButton("Zurück") { _,_ -> if(continuation.isActive) continuation.resumeWith(Result.success(false)) }
                .setPositiveButton("Ohne Übernahme beenden") { _,_ -> if(continuation.isActive) continuation.resumeWith(Result.success(true)) }
                .create()
            dialog.setOnDismissListener { if(continuation.isActive) continuation.resumeWith(Result.success(false)) }
            continuation.invokeOnCancellation { fragment.activity?.runOnUiThread { dialog.dismiss() } }
            dialog.show()
        }
    }

    fun bind(fragment: Fragment, root: View, target: suspend () -> BtbActiveContext.Result?,
             commit: suspend (SpeechDraft, Int, String, String?, Long) -> Unit) {
        val c=fragment.requireContext()
        val badges=mapOf(4 to R.id.btnSpeech4,5 to R.id.btnSpeech5,6 to R.id.btnSpeech6,7 to R.id.btnSpeech7,8 to R.id.btnSpeech8)
        fun open(section:Int?) { fragment.viewLifecycleOwner.lifecycleScope.launch {
            val t=target() ?: return@launch
            val firms=t.entry.firmen.filter{!it.isDeleted}.map{it.firmaId}.toSet()
            val drafts=withContext(Dispatchers.IO){SpeechDraftStore.list(c,t.btbId)}.filter {
                SpeechDraftPolicy.bucket(it.copy(firmId=it.firmId?.takeIf { id -> id.isEmpty() || id in firms }))==section
            }
            if(drafts.isEmpty())return@launch
            MaterialAlertDialogBuilder(c).setTitle(if(section==null)"Spracheingaben zuordnen" else "Spracheingaben prüfen")
                .setItems(drafts.map { it.text.take(90) }.toTypedArray()) { _,i -> edit(fragment,drafts[i],target,commit) }
                .setNegativeButton("Schließen",null).show()
        } }
        badges.forEach { (section,id) -> root.findViewById<View>(id).setOnClickListener { open(section) } }
        root.findViewById<View>(R.id.btnSpeechInbox).setOnClickListener { open(null) }
        root.findViewById<View>(R.id.btnSpeechRecord).setOnClickListener {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val t=target()
                if(t==null){Toast.makeText(c,"Bitte zuerst einen BTB öffnen",Toast.LENGTH_LONG).show();return@launch}
                fragment.startActivity(Intent(c,SpeechCaptureActivity::class.java).putExtra("btb",t.btbId).putExtra("auto",true))
            }
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SpeechDraftStore.changes.collect {
                    val t=target()
                    val firms=t?.entry?.firmen?.filter{!it.isDeleted}?.map{it.firmaId}.orEmpty().toSet()
                    val drafts=if(t==null)emptyList() else withContext(Dispatchers.IO){SpeechDraftStore.list(c,t.btbId)}
                    val groups=drafts.groupBy { SpeechDraftPolicy.bucket(it.copy(firmId=it.firmId?.takeIf { id -> id.isEmpty() || id in firms })) }
                    fun badge(id:Int,section:Int?) {
                        val b=root.findViewById<Button>(id);val count=groups[section]?.size ?: 0
                        b.visibility=if(count==0)View.GONE else View.VISIBLE;b.text="🎙 $count"
                        b.contentDescription="$count Spracheingaben ${if(section==null) "zuordnen" else "in ${SpeechDraftPolicy.sections[section]} prüfen"}"
                    }
                    badges.forEach{(point,id)->badge(id,point)};badge(R.id.btnSpeechInbox,null)
                }
            }
        }
    }
    private fun edit(fragment:Fragment, draft:SpeechDraft, target:suspend()->BtbActiveContext.Result?,
                     commit:suspend(SpeechDraft,Int,String,String?,Long)->Unit) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val t=target() ?: return@launch
            if(t.btbId!=draft.btbId)return@launch
            val c=fragment.requireContext()
            val firms=t.entry.firmen.filter{!it.isDeleted}
            val nodes=withContext(Dispatchers.IO) {
                ServiceLocator.baustellenBaumRepository.hauptNachAltBaustelleId(t.baustelleId)?.let { root ->
                    ServiceLocator.baustellenBaumRepository.alleKnotenZuRootSofort(root.id).filter{it.id!=root.id}
                }.orEmpty()
            }
            val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,16)}
            val scroll=ScrollView(c).apply { isFillViewport=true;addView(box) }
            fun label(s:String){box.addView(TextView(c).apply{text=s;setPadding(0,16,0,4)})}
            fun spinner(values:List<String>,selected:Int):Spinner=Spinner(c).also {
                it.adapter=ArrayAdapter(c,android.R.layout.simple_spinner_dropdown_item,values);it.setSelection(selected.coerceAtLeast(0));box.addView(it)
            }
            label("Text")
            val input=EditText(c).apply{setText(draft.text);minLines=3;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE}
            box.addView(input)
            label("Firma")
            val firmIds=listOf<String?>(null,"")+firms.map{it.firmaId}
            val firm=spinner(listOf("Firma wählen","Allgemein – bewusst ohne Firma")+firms.map{it.name},firmIds.indexOf(draft.firmId))
            label("BTB-Punkt")
            val points=listOf<Int?>(null)+SpeechDraftPolicy.sections.keys
            val section=spinner(listOf("Abschnitt wählen")+SpeechDraftPolicy.sections.map{"${it.key}. ${it.value}"},points.indexOf(draft.section))
            label("Unterbaustelle")
            val places=listOf<Long?>(null,0L)+nodes.map{it.id}
            val place=spinner(listOf("Unterbaustelle wählen","Hauptbaustelle")+nodes.map{BaustellenObjektLogik.unterbaustellenName(it).orEmpty().ifBlank{"Ort ${it.id}"}},places.indexOf(draft.nodeId))
            val hint=TextView(c).apply{text="Entwurf bleibt bis zum Speichern nur auf diesem Handy. Zeit und KM werden nicht geraten.";setPadding(0,16,0,8)};box.addView(hint)
            val discard=Button(c).apply{text="Entwurf verwerfen"};box.addView(discard)
            var finished=false
            var saving=false
            val dialog=MaterialAlertDialogBuilder(c).setTitle("Spracheingabe bearbeiten").setView(scroll)
                .setNegativeButton("Schließen",null).setPositiveButton("Speichern",null).create()
            fun edited()=draft.copy(text=input.text.toString().take(12000),firmId=firmIds[firm.selectedItemPosition],section=points[section.selectedItemPosition],nodeId=places[place.selectedItemPosition])
            fun keep(){if(!finished && !saving)runCatching{SpeechDraftStore.put(c,edited())}.onFailure{hint.text="Änderungen nicht gesichert. Ursprünglicher Entwurf bleibt erhalten."}}
            input.addTextChangedListener(object:android.text.TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){keep()}
                override fun afterTextChanged(s:android.text.Editable?){}
            })
            val selection=object:AdapterView.OnItemSelectedListener{
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){keep()}
                override fun onNothingSelected(parent:AdapterView<*>?){}
            }
            listOf(firm,section,place).forEach{it.onItemSelectedListener=selection}
            discard.setOnClickListener {
                MaterialAlertDialogBuilder(c).setTitle("Sprachentwurf verwerfen?").setMessage("Nur dieser lokale Entwurf wird entfernt.")
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Verwerfen"){_,_->
                        runCatching{SpeechDraftStore.remove(c,draft)}.onSuccess{finished=true;dialog.dismiss()}.onFailure{hint.text="Entwurf konnte nicht entfernt werden."}
                    }.show()
            }
            dialog.setOnDismissListener{keep()}
            dialog.setOnShowListener{
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val d=edited();val point=d.section;val node=d.nodeId
                    if(point==null || node==null || d.firmId==null || !SpeechDraftPolicy.hasDescription(d.text, firms.firstOrNull{it.firmaId==d.firmId}?.name)){hint.text="Bitte Text, Firma/Allgemein, Abschnitt und Unterbaustelle auswählen.";return@setOnClickListener}
                    if(saving)return@setOnClickListener
                    saving=true;dialog.getButton(-1).isEnabled=false;discard.isEnabled=false
                    dialog.setCancelable(false);dialog.getButton(-2).isEnabled=false
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            commit(d,point,d.text.trim(),d.firmId.takeIf{it.isNotBlank()},node)
                            SpeechDraftStore.remove(c,d);finished=true;dialog.dismiss()
                        } catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                        catch(_:Exception){hint.text="Nicht übernommen. BTB, Rechte und Verbindung zum lokalen Speicher erneut prüfen. Entwurf bleibt erhalten."}
                        finally {saving=false;dialog.setCancelable(true);dialog.getButton(-1)?.isEnabled=true;dialog.getButton(-2)?.isEnabled=true;discard.isEnabled=true}
                    }
                }
            }
            dialog.show()
        }
    }
}
