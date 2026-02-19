let interval;

function validate() {
    const url = document.getElementById("url").value;
    document.getElementById("download").disabled = !url;
}

function start() {

    const url = document.getElementById("url").value;

    fetch("http://localhost:8000/download?url=" + encodeURIComponent(url), {
         method: "POST"
    })
    


    .then(() => {
        document.getElementById("status").innerText = "Downloading...";
        startPolling();
    })
    .catch(() => {
        document.getElementById("status").innerText = "Failed to start download";
    });
}

function startPolling() {

    clearInterval(interval);

    interval = setInterval(() => {

        fetch("http://localhost:8000/progress")
        .then(res => res.json())
        .then(data => {

            // ========================
            // UPDATE OVERALL BAR
            // ========================
            const overallBar = document.getElementById("progress");
            overallBar.style.width = data.overall + "%";
            overallBar.innerText = data.overall + "%";

            // ========================
            // THREAD VISUALIZATION
            // ========================
            updateThreads(data.threads);

            if (data.overall >= 100) {
                clearInterval(interval);
                document.getElementById("status").innerText =
        "Download completed and saved to Downloads folder!";
                

            }
        });

    }, 500);
}

function updateThreads(threads) {

    let container = document.getElementById("threadContainer");

    // If not exists, create dynamically
    if (!container) {
        container = document.createElement("div");
        container.id = "threadContainer";
        document.querySelector(".card").appendChild(container);
    }

    container.innerHTML = "";

    threads.forEach((thread, index) => {

        const box = document.createElement("div");
        box.className = "thread-box";

        box.innerHTML = `
            <div class="thread-label">
                Thread ${index + 1} - ${thread.status}
            </div>
            <div class="thread-bar">
                <div class="thread-fill"
                     style="width:${thread.progress}%">
                </div>
            </div>
        `;

        container.appendChild(box);
    });
}
function toggleAdvanced() {
    const section = document.getElementById("advanced-section");

    if (section.style.display === "none") {
        section.style.display = "block";
    } else {
        section.style.display = "none";
    }
}

