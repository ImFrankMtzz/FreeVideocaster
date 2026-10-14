' FreeVideocaster: canal receptor (v2).
'
' Recibe órdenes por ECP de dos formas:
'   - Al abrir el canal:      POST http://ROKU:8060/launch/dev?url=...&title=...
'   - Con el canal abierto:   POST http://ROKU:8060/input?url=...&title=...
'
' Parámetros (ver README): action, url, title, format, sub, subs, headers, pos, delta,
' key (identificador opaco que se devuelve en el estado), start (segundos), index
' (posición en la cola) e id (identificador de pista de audio o de subtítulos).
' También acepta los nombres de "Play on Roku" (u, videoName, videoFormat) y contentId.
'
' Estado en vivo: mientras el canal está abierto, GET http://ROKU:8061/status devuelve
' un JSON con lo que se reproduce, la posición y la cola. Es solo lectura.
'
' Enlaces largos: si un valor no cabe en una sola petición, la app envía la consulta
' completa troceada con /input?cid=ID&i=N&n=TOTAL&chunk=TROZO y aquí se reconstruye.

sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    scene = screen.CreateScene("PlayerScene")
    screen.Show()

    input = CreateObject("roInput")
    input.SetMessagePort(port)

    server = StartStatusServer(port)
    pending = {}  ' Trozos recibidos por identificador (cid)
    HandleParams(scene, args, pending)

    while true
        msg = Wait(0, port)
        msgType = Type(msg)
        if msgType = "roSGScreenEvent"
            if msg.IsScreenClosed() then return
        else if msgType = "roInputEvent"
            if msg.IsInput() then HandleParams(scene, msg.GetInfo(), pending)
        else if msgType = "roSocketEvent"
            if server <> invalid then HandleSocketEvent(msg, server, scene)
        end if
    end while
end sub

sub HandleParams(scene as Object, params as Dynamic, pending as Object)
    if Type(params) <> "roAssociativeArray" then return

    chunk = FirstValue(params, ["chunk"])
    if chunk <> ""
        query = AddChunk(pending, params, chunk)
        if query <> "" then HandleParams(scene, ParseQuery(query), pending)
        return
    end if

    url = FirstValue(params, ["url", "u", "contentId"])
    ' Por si el enlace llega todavía codificado (https%3A%2F%2F...).
    if LCase(Left(url, 8)) = "https%3a" or LCase(Left(url, 7)) = "http%3a"
        url = CreateObject("roUrlTransfer").Unescape(url)
    end if

    action = LCase(FirstValue(params, ["action"]))
    if action = "" and url <> "" then action = "play"
    if action = "" then return

    if url <> "" then print "[FreeVideocaster] "; action; " ("; Len(url); " caracteres): "; url

    scene.request = {
        action: action
        url: url
        title: FirstValue(params, ["title", "videoName"])
        format: LCase(FirstValue(params, ["format", "videoFormat"]))
        subtitle: FirstValue(params, ["sub", "subtitle"])
        subs: FirstValue(params, ["subs"])
        headers: FirstValue(params, ["headers"])
        pos: FirstValue(params, ["pos"])
        delta: FirstValue(params, ["delta"])
        key: FirstValue(params, ["key"])
        start: FirstValue(params, ["start"])
        index: FirstValue(params, ["index"])
        id: FirstValue(params, ["id"])
    }
end sub

' Guarda un trozo y, si ya llegaron todos, devuelve la consulta completa.
function AddChunk(pending as Object, params as Object, chunk as String) as String
    cid = FirstValue(params, ["cid"])
    index = Int(Val(FirstValue(params, ["i"])))
    total = Int(Val(FirstValue(params, ["n"])))
    if cid = "" or total < 1 or index < 0 or index >= total then return ""

    if not pending.DoesExist(cid)
        pending.Clear()  ' Solo interesa la última transferencia
        pending[cid] = { parts: CreateObject("roArray", total, false), count: 0 }
    end if
    entry = pending[cid]
    if entry.parts[index] = invalid then entry.count = entry.count + 1
    entry.parts[index] = chunk
    if entry.count < total then return ""

    query = ""
    for i = 0 to total - 1
        query = query + entry.parts[i]
    end for
    pending.Delete(cid)
    return query
end function

function ParseQuery(query as String) as Object
    result = {}
    transfer = CreateObject("roUrlTransfer")
    for each pair in query.Split("&")
        separator = Instr(1, pair, "=")
        if separator > 0
            result[transfer.Unescape(Left(pair, separator - 1))] = transfer.Unescape(Mid(pair, separator + 1))
        end if
    end for
    return result
end function

function FirstValue(params as Object, keys as Object) as String
    for each key in keys
        value = params[key]
        if GetInterface(value, "ifString") <> invalid
            if value <> "" then return value
        end if
    end for
    return ""
end function

' --- Estado en vivo (HTTP, puerto 8061, solo lectura) --------------------------------------

function StartStatusServer(port as Object) as Object
    listener = CreateObject("roStreamSocket")
    listener.SetMessagePort(port)
    listener.SetReuseAddr(true)
    address = CreateObject("roSocketAddress")
    address.SetPort(8061)
    listener.SetAddress(address)
    listener.NotifyReadable(true)
    if not listener.Listen(4) or not listener.eOK()
        print "[FreeVideocaster] No se pudo abrir el puerto de estado 8061"
        return invalid
    end if
    print "[FreeVideocaster] Estado en vivo en el puerto 8061"
    return { listener: listener, port: port, clients: {} }
end function

sub HandleSocketEvent(msg as Object, server as Object, scene as Object)
    id = msg.GetSocketID()
    listener = server.listener

    if id = listener.GetID()
        if listener.IsReadable()
            client = listener.Accept()
            if client <> invalid and client.eOK()
                client.SetMessagePort(server.port)
                client.NotifyReadable(true)
                server.clients[client.GetID().ToStr()] = client
            end if
        end if
        return
    end if

    key = id.ToStr()
    client = server.clients[key]
    if client = invalid then return

    buffer = CreateObject("roByteArray")
    buffer[1023] = 0
    received = client.Receive(buffer, 0, 1024)
    if received > 0
        request = buffer.ToAsciiString()
        crlf = Chr(13) + Chr(10)
        if Left(request, 11) = "GET /status"
            body = FormatJson(scene.status)
            client.SendStr("HTTP/1.0 200 OK" + crlf + "Content-Type: application/json; charset=utf-8" + crlf + "Cache-Control: no-store" + crlf + crlf + body)
        else
            client.SendStr("HTTP/1.0 404 Not Found" + crlf + crlf)
        end if
    end if
    ' Una respuesta por conexión; también se cierra si el cliente colgó (received = 0).
    client.Close()
    server.clients.Delete(key)
end sub
