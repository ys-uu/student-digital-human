// 建立ws连接
function connectWs(token) {
    const ws = new WebSocket(`ws://localhost:8080/ws/chat?token=${token}`)
    ws.onopen = () => {
        console.log("ws连接成功")
    }
    ws.onmessage = (event) => {
        console.log("收到数字人消息：",event.data)
    }
    ws.onclose = () => {
        console.log("ws关闭")
    }
    ws.onerror = (err) => {
        console.error("ws异常",err)
    }
    return ws
}
