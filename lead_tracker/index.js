let myLeads = []
let oldLeads = []
const inputEl =  document.getElementById("input-el")
const inputBtn =  document.getElementById("input-btn")
const ulEl = document.getElementById("ul-el")
const deleteBtn = document.getElementById("delete-btn")
const tabBtn = document.getElementById("tab-btn")
const leadsFromLocalStorage = JSON.parse(localStorage.getItem("myLeads"))


if(leadsFromLocalStorage){
    myLeads = leadsFromLocalStorage
    render(myLeads)
}


function render(leads){
    let listItems = ""
    for(let i = 0; i < leads.length; i++){
        //listItems += "<li><a target='_blank' href='" + myLeads[i] + "'>" + myLeads[i] + "</a></li>"
        listItems += `
                <li>
                    <a target='_blank' href='${leads[i]}'>
                        ${leads[i]}
                    </a>
                </li>
                `
        
        /*const li = document.createElement("li")
        li.textContent = myLeads[i]
        ulEl.append(li)*/
    }
    ulEl.innerHTML = listItems
    }


tabBtn.addEventListener("click", function(){
    chrome.tabs.query({active: true, currentWindow: true}, function(tabs){
        myLeads.push(tabs[0].url)
        localStorage.setItem("myLeads", JSON.stringify(myLeads) )
        render(myLeads)

    })
})    


deleteBtn.addEventListener("dblclick", function(){
    myLeads = []
    localStorage.clear()
    render(myLeads)

})

inputBtn.addEventListener("click", function(){
    myLeads.push(inputEl.value)
    inputEl.value = ""
    localStorage.setItem("myLeads", JSON.stringify(myLeads))
    render(myLeads)
    console.log( localStorage.getItem("myLeads") )
    
})

