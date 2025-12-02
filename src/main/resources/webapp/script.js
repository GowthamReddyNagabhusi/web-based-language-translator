document.getElementById("translateBtn").addEventListener("click", () => {
    const text = document.getElementById("inputText").value;
    const lang = document.getElementById("language").value;

    const params = new URLSearchParams();
    params.append("text", text);
    params.append("lang", lang);

    fetch("/translate", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: params
    })
        .then(res => res.text())
        .then(data => {
            document.getElementById("output").innerText = data;
        })
        .catch(err => console.error(err));
});
