function validate() {
    const url = document.getElementById("url").value;
    const btn = document.getElementById("download");

    try {
        new URL(url);
        btn.disabled = false;
    } catch {
        btn.disabled = true;
    }
}

function start() {
    const url = document.getElementById("url").value;
    const name = document.getElementById("filename").value.trim();
    const status = document.getElementById("status");
    const bar = document.getElementById("progress");

    bar.style.width = "0%";
    status.innerText = "Starting download...";

    const xhr = new XMLHttpRequest();

    xhr.open(
        "GET",
        "http://localhost:8080/download?" +
        "url=" + encodeURIComponent(url) +
        "&name=" + encodeURIComponent(name)
    );

    xhr.responseType = "blob";

    // ✅ DOWNLOAD PROGRESS (server → browser)
    xhr.onprogress = (e) => {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            bar.style.width = percent + "%";
            status.innerText = "Downloading: " + percent.toFixed(1) + "%";
        } else {
            status.innerText = "Downloading...";
        }
    };

    xhr.onload = () => {

        // ❌ ERROR RESPONSE
        if (xhr.status !== 200) {
            const reader = new FileReader();

            reader.onload = () => {
                status.innerText = reader.result || "Download failed";
            };

            reader.readAsText(xhr.response);
            return;
        }

        // ✅ SUCCESS
        const blob = xhr.response;
        const a = document.createElement("a");
        const downloadUrl = URL.createObjectURL(blob);

        a.href = downloadUrl;
        const userName = document.getElementById("filename").value.trim();

        a.download = userName
        ? userName
        : "downloaded_file";
// use filename from backend
        document.body.appendChild(a);
        a.click();
        a.remove();

        URL.revokeObjectURL(downloadUrl);

        bar.style.width = "100%";
        status.innerText = "Download completed";
    };

    xhr.onerror = () => {
        status.innerText = "Network error (connection failed)";
    };

    xhr.send();
}
