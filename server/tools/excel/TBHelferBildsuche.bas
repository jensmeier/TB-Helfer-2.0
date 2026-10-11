Attribute VB_Name = "TBHelferBildsuche"
Option Explicit

' Optionales Werkzeug fuer Excel Desktop unter Windows.
' Keine Bilddatei wird veraendert. Mehrdeutige Namen bleiben unveraendert.
Public Sub BilderSuchen()
    Dim ws As Worksheet, wb As Workbook, report As Worksheet
    Dim fso As Object, index As Object, counts As Object
    Dim picker As Object, root As String, name As String, target As String
    Dim col As Long, lastRow As Long, r As Long, c As Long, reportRow As Long
    Dim found As Long, missing As Long, ambiguous As Long, skipped As Long
    Dim oldScreen As Boolean, oldEvents As Boolean, running As Boolean
    Dim cell As Range, oldLink As String, state As String, ext As String
    On Error GoTo Failed
    If TypeName(ActiveSheet) <> "Worksheet" Then Exit Sub
    Set ws = ActiveSheet
    Set wb = ws.Parent
    If wb.ReadOnly Or ws.ProtectContents Or wb.ProtectStructure Then
        MsgBox "Bitte eine bearbeitbare, ungeschuetzte Kopie der Bilderliste oeffnen.", vbExclamation
        Exit Sub
    End If
    For c = 1 To ws.Cells(1, ws.Columns.Count).End(xlToLeft).Column
        If Not IsError(ws.Cells(1, c).Value2) Then
            If LCase$(Trim$(CStr(ws.Cells(1, c).Value2))) = "bilddatei" Then
                If col <> 0 Then
                    MsgBox "Mehrere Spalten mit dem Titel Bilddatei gefunden.", vbExclamation
                    Exit Sub
                End If
                col = c
            End If
        End If
    Next c
    If col = 0 Then
        MsgBox "Bitte das Blatt mit der Ueberschrift Bilddatei in Zeile 1 auswaehlen.", vbInformation
        Exit Sub
    End If
    If Len(wb.Path) = 0 Or InStr(1, wb.Path, "://", vbTextCompare) > 0 Then
        MsgBox "Bitte die Arbeitsmappe zuerst in einem lokalen Ordner speichern.", vbInformation
        Exit Sub
    End If
    Set picker = Application.FileDialog(4)
    picker.Title = "Entpackten Baustellenordner mit den Bildern waehlen"
    picker.AllowMultiSelect = False
    picker.InitialFileName = wb.Path & Application.PathSeparator
    If picker.Show <> -1 Then Exit Sub
    root = picker.SelectedItems(1)
    If MsgBox("Bildlinks werden nur bei genau einem passenden Dateinamen aktualisiert." & vbCrLf & _
        "Mehrere Treffer bleiben unveraendert. Es wird nichts automatisch gespeichert." & vbCrLf & _
        "Suche starten?", vbOKCancel + vbInformation) <> vbOK Then Exit Sub

    Set fso = CreateObject("Scripting.FileSystemObject")
    Set index = CreateObject("Scripting.Dictionary")
    Set counts = CreateObject("Scripting.Dictionary")
    index.CompareMode = vbTextCompare
    counts.CompareMode = vbTextCompare
    oldScreen = Application.ScreenUpdating
    oldEvents = Application.EnableEvents
    running = True
    Application.ScreenUpdating = False
    Application.EnableEvents = False
    Application.StatusBar = "TB-Helfer: Bilderordner werden durchsucht ..."
    ' Bei einem Lesefehler wird vor jeder Linkaenderung abgebrochen.
    IndexFolders fso, root, index, counts, skipped
    Set report = wb.Worksheets.Add(After:=wb.Worksheets(wb.Worksheets.Count))
    report.Name = ReportName(wb)
    report.Cells.NumberFormat = "@"
    report.Cells(1, 1).Value2 = "Blatt"
    report.Cells(1, 2).Value2 = "Zeile"
    report.Cells(1, 3).Value2 = "Bilddatei"
    report.Cells(1, 4).Value2 = "Ergebnis"
    report.Cells(1, 5).Value2 = "Bisheriger Link"
    report.Cells(1, 6).Value2 = "Neuer Link"
    reportRow = 2
    lastRow = ws.Cells(ws.Rows.Count, col).End(xlUp).Row
    For r = 2 To lastRow
        Set cell = ws.Cells(r, col)
        oldLink = ""
        target = ""
        state = ""
        If cell.Hyperlinks.Count > 0 Then oldLink = cell.Hyperlinks(1).Address
        If IsError(cell.Value2) Then
            name = ""
            state = "Zellfehler - unveraendert"
        Else
            name = Trim$(CStr(cell.Value2))
            If Len(name) = 0 Then GoTo NextRow
            If cell.HasFormula Or cell.Hyperlinks.Count > 1 Then
                state = "Formel/mehrere Links - unveraendert"
            ElseIf fso.GetFileName(name) <> name Or Not ImageName(fso, name) Then
                state = "Kein einfacher Bilddateiname - unveraendert"
            ElseIf Not counts.Exists(name) Then
                missing = missing + 1
                state = "Nicht gefunden - unveraendert"
            ElseIf counts(name) <> 1 Then
                ambiguous = ambiguous + 1
                state = CStr(counts(name)) & " Treffer - unveraendert"
            Else
                target = RelativeTarget(fso, wb.Path, CStr(index(name)))
                If cell.Hyperlinks.Count = 1 Then
                    cell.Hyperlinks(1).Address = target
                    cell.Hyperlinks(1).SubAddress = ""
                Else
                    ws.Hyperlinks.Add Anchor:=cell, Address:=target, TextToDisplay:=name
                End If
                found = found + 1
                state = "Eindeutig zugeordnet"
            End If
        End If
        report.Cells(reportRow, 1).Value2 = ws.Name
        report.Cells(reportRow, 2).Value2 = CStr(r)
        report.Cells(reportRow, 3).Value2 = name
        report.Cells(reportRow, 4).Value2 = state
        report.Cells(reportRow, 5).Value2 = oldLink
        report.Cells(reportRow, 6).Value2 = target
        reportRow = reportRow + 1
NextRow:
    Next r
    report.Rows(1).Font.Bold = True
    report.Columns("A:D").AutoFit
    report.Columns("E:F").ColumnWidth = 55
    report.Range("A1:F" & CStr(reportRow - 1)).AutoFilter
    report.Activate
    Application.ScreenUpdating = oldScreen
    Application.EnableEvents = oldEvents
    Application.StatusBar = False
    running = False
    MsgBox CStr(found) & " eindeutig zugeordnet" & vbCrLf & _
        CStr(missing) & " nicht gefunden" & vbCrLf & _
        CStr(ambiguous) & " mehrdeutig" & vbCrLf & _
        CStr(skipped) & " Ordnerverknuepfungen ausgelassen" & vbCrLf & _
        "Bitte das Suchprotokoll pruefen und die Arbeitsmappe als Kopie speichern." & vbCrLf & _
        "Original/Kleinversion werden ohne gemeinsame Bild-ID nicht automatisch unterschieden.", vbInformation
    Exit Sub
Failed:
    Dim message As String
    message = Err.Description
    If running Then
        Application.ScreenUpdating = oldScreen
        Application.EnableEvents = oldEvents
        Application.StatusBar = False
    End If
    MsgBox "Suche abgebrochen: " & message & vbCrLf & _
        "Es wurde nichts automatisch gespeichert. Eventuelle Teilaenderungen pruefen oder ohne Speichern schliessen.", vbExclamation
End Sub

Private Function ImageName(ByVal fso As Object, ByVal name As String) As Boolean
    Select Case LCase$(fso.GetExtensionName(name))
        Case "jpg", "jpeg", "png", "webp", "heic", "heif", "tif", "tiff", "bmp"
            ImageName = True
    End Select
End Function

Private Sub IndexFolders(ByVal fso As Object, ByVal root As String, ByVal index As Object, ByVal counts As Object, ByRef skipped As Long)
    Dim pending As New Collection, folder As Object, child As Object, file As Object
    Dim path As String, name As String
    pending.Add root
    Do While pending.Count > 0
        path = CStr(pending(pending.Count))
        pending.Remove pending.Count
        Set folder = fso.GetFolder(path)
        If (folder.Attributes And 1024) <> 0 Then
            skipped = skipped + 1
        Else
            For Each file In folder.Files
                If (file.Attributes And 1024) = 0 And ImageName(fso, file.Name) Then
                    name = file.Name
                    If counts.Exists(name) Then
                        counts(name) = CLng(counts(name)) + 1
                    Else
                        counts.Add name, 1
                        index.Add name, file.Path
                    End If
                End If
            Next file
            For Each child In folder.SubFolders
                pending.Add child.Path
            Next child
        End If
    Loop
End Sub

Private Function RelativeTarget(ByVal fso As Object, ByVal base As String, ByVal target As String) As String
    Dim a As Variant, b As Variant, i As Long, j As Long, result As String
    base = fso.GetAbsolutePathName(base)
    target = fso.GetAbsolutePathName(target)
    If StrComp(fso.GetDriveName(base), fso.GetDriveName(target), vbTextCompare) <> 0 Then
        RelativeTarget = target
        Exit Function
    End If
    If Right$(base, 1) = "\" Then base = Left$(base, Len(base) - 1)
    a = Split(base, "\")
    b = Split(target, "\")
    i = 0
    Do While i <= UBound(a) And i <= UBound(b)
        If StrComp(CStr(a(i)), CStr(b(i)), vbTextCompare) <> 0 Then Exit Do
        i = i + 1
    Loop
    For j = i To UBound(a)
        result = result & "../"
    Next j
    For j = i To UBound(b)
        result = result & b(j)
        If j < UBound(b) Then result = result & "/"
    Next j
    RelativeTarget = result
End Function

Private Function ReportName(ByVal wb As Workbook) As String
    Dim n As Long, candidate As String, ws As Worksheet, exists As Boolean
    n = 1
    Do
        candidate = "Bildsuche_" & CStr(n)
        exists = False
        For Each ws In wb.Worksheets
            If StrComp(ws.Name, candidate, vbTextCompare) = 0 Then exists = True
        Next ws
        If Not exists Then
            ReportName = candidate
            Exit Function
        End If
        n = n + 1
    Loop
End Function

Public Sub SuchknopfAnlegen()
    Dim ws As Worksheet, c As Long, button As Object, col As Long
    If TypeName(ActiveSheet) <> "Worksheet" Then Exit Sub
    Set ws = ActiveSheet
    For c = 1 To ws.Cells(1, ws.Columns.Count).End(xlToLeft).Column
        If Not IsError(ws.Cells(1, c).Value2) Then
            If LCase$(Trim$(CStr(ws.Cells(1, c).Value2))) = "bilddatei" Then col = c
        End If
    Next c
    If col = 0 Or ws.ProtectContents Then
        MsgBox "Bitte ein ungeschuetztes Bilderlistenblatt auswaehlen.", vbInformation
        Exit Sub
    End If
    On Error Resume Next
    Set button = ws.Shapes("TBHelferBildsucheKnopf")
    On Error GoTo 0
    If Not button Is Nothing Then Exit Sub
    c = ws.Cells(1, ws.Columns.Count).End(xlToLeft).Column + 2
    Set button = ws.Shapes.AddFormControl(xlButtonControl, ws.Cells(2, c).Left, ws.Cells(2, c).Top, 190, 30)
    button.Name = "TBHelferBildsucheKnopf"
    button.TextFrame.Characters.Text = "Bilder suchen / Links reparieren"
    button.OnAction = "'" & Replace(ws.Parent.Name, "'", "''") & "'!BilderSuchen"
    button.Placement = xlFreeFloating
End Sub
