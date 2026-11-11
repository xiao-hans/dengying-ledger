package com.meow.ledger.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meow.ledger.shared.db.Bill_book
import com.meow.ledger.shared.db.Member_book
import com.meow.ledger.shared.manage.BookRepository
import com.meow.ledger.shared.manage.DEFAULT_BOOK_ID
import com.meow.ledger.shared.manage.LOCAL_MEMBER_ID
import com.meow.ledger.shared.manage.ROLE_OWNER
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

/** 账本列表、新建编辑、详情、扫码加入与成员。页面键：books / book-new / book-edit:<id> / book:<id> / book-join / book-members:<id>。 */
@Composable
fun BooksPage(repository: BookRepository, page: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var notice by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().background(palette.appBg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            page == "books" -> BookList(repository, onOpen, onBack) { notice = it }
            page == "book-new" -> BookForm(repository, null, onOpen, onBack) { notice = it }
            page.startsWith("book-edit:") -> BookForm(repository, page.substringAfter(":").toLongOrNull(), onOpen, onBack) { notice = it }
            page == "book-join" -> JoinBook(repository, onBack) { notice = it }
            page.startsWith("book-members:") -> BookMembers(repository, page.substringAfter(":").toLongOrNull() ?: DEFAULT_BOOK_ID, onBack) { notice = it }
            page.startsWith("book:") -> BookDetail(repository, page.substringAfter(":").toLongOrNull() ?: DEFAULT_BOOK_ID, onOpen, onBack) { notice = it }
        }
        if (notice.isNotEmpty()) Text(notice, color = palette.danger, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun BookList(repository: BookRepository, onOpen: (String) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var books by remember { mutableStateOf(repository.books()) }
    var pendingDelete by remember { mutableStateOf<Bill_book?>(null) }
    BackHeader("账本管理", onBack)
    Text("共 ${books.size} 本账。默认账本不可删除，月起始日影响每月统计区间。", color = palette.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        ChoiceChip("新建账本", true) { onOpen("book-new") }
        ChoiceChip("加入账本", false) { onOpen("book-join") }
    }
    if (books.isEmpty()) Empty("还没有账本", "新建一本账本开始记账", "新建账本") { onOpen("book-new") }
    books.forEach { book ->
        val members = repository.members(book.id).size
        GlassCard(onClick = { onOpen("book:${book.id}") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(book.name, color = palette.textPrimary, modifier = Modifier.weight(1f))
                if (book.id == DEFAULT_BOOK_ID) Text("默认", color = palette.brand)
            }
            Text("$members 位成员 · 每月 ${book.month_start_date} 号起始", color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
    val custom = books.firstOrNull { it.id != DEFAULT_BOOK_ID }
    if (custom != null) {
        ActionButton("删除自定义账本「${custom.name}」", true) { pendingDelete = custom }
    }
    pendingDelete?.let { target ->
        ConfirmBar(
            "删除账本「${target.name}」？账单仍保留在设备上，只是不再显示这本账本。",
            onCancel = { pendingDelete = null },
            onConfirm = {
                if (repository.delete(target.id)) {
                    books = repository.books()
                    onNotice("账本已删除")
                } else onNotice("默认账本不能删除")
                pendingDelete = null
            },
            confirmLabel = "删除",
        )
    }
}

@Composable
private fun BookForm(repository: BookRepository, id: Long?, onOpen: (String) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    val existing = id?.let { repository.book(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var iconId by remember { mutableStateOf(existing?.icon_id ?: 1L) }
    var start by remember { mutableStateOf((existing?.month_start_date ?: 1L).toInt()) }
    BackHeader(if (existing == null) "新建账本" else "编辑账本", onBack)
    Field("账本名称", name, { name = it }, "例如 日常 / 旅行 / 家庭")
    Text("每月起始日", color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 5, 10, 15, 20, 28).forEach { day -> ChoiceChip("$day 号", start == day) { start = day } }
    }
    Text("账本图标", color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BOOK_ICONS.forEach { (icon, label) -> ChoiceChip(label, iconId == icon) { iconId = icon } }
    }
    Text("月起始日用于月度报表与预算区间，已有账单的日期不会被改写。", color = palette.textSecondary, modifier = Modifier.padding(top = 8.dp))
    ActionButton("保存账本", name.isNotBlank()) {
        val saved = if (existing == null) repository.create(name.trim(), iconId, start)
        else repository.update(existing.id, name.trim(), iconId, start)
        if (saved == null) onNotice("保存失败") else { onNotice(if (existing == null) "账本已创建" else "账本已更新"); onOpen("book:${saved.id}") }
    }
}

@Composable
private fun BookDetail(repository: BookRepository, id: Long, onOpen: (String) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    val book = repository.book(id)
    if (book == null) { Missing("账本不存在", onBack); return }
    var code by remember { mutableStateOf("") }
    var confirmExit by remember { mutableStateOf(false) }
    val owner = repository.roleOf(id) == ROLE_OWNER
    BackHeader(book.name, onBack)
    GlassCard {
        Text("${repository.members(id).size} 位成员 · ${if (owner) "我创建" else "我加入"}", color = palette.textSecondary)
        Text("每月 ${book.month_start_date} 号起始", color = palette.textPrimary, modifier = Modifier.padding(top = 6.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("编辑", false) { onOpen("book-edit:$id") }
        ChoiceChip("成员", false) { onOpen("book-members:$id") }
    }
    Text("邀请码", color = palette.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    if (code.isEmpty()) Text("生成后分享给他人。本地版不连接真实协作服务。", color = palette.textSecondary)
    else Text(code, color = palette.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
    ActionButton(if (code.isEmpty()) "生成邀请码" else "重新生成", true) { code = repository.inviteCode(id); onNotice("邀请码已生成，请手动复制") }
    if (id == DEFAULT_BOOK_ID) Text("默认账本不可退出。", color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp))
    else ActionButton("退出账本", true) { confirmExit = true }
    if (confirmExit) ConfirmBar(
        "退出这本账本？账单仍保留在设备上，只是不再显示这本账本。",
        onCancel = { confirmExit = false },
        onConfirm = {
            if (repository.exit(id)) onBack() else onNotice("退出失败")
            confirmExit = false
        },
        confirmLabel = "退出",
    )
}

@Composable
private fun JoinBook(repository: BookRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    BackHeader("加入账本", onBack)
    Text("扫一扫对方的账本二维码，或把分享来的邀请码粘贴到下面，校验通过后加入，两条路走同一条链路。", color = LocalPalette.current.textSecondary)
    Field("邀请码", code, { code = it }, "例如 DY1ABC23")
    ActionButton("加入账本", code.isNotBlank()) {
        val joined = repository.join(code.trim())
        onNotice("已加入「${joined.name}」")
        onBack()
    }
}

@Composable
private fun BookMembers(repository: BookRepository, bookId: Long, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    val book = repository.book(bookId)
    var rows by remember { mutableStateOf(repository.members(bookId)) }
    var pending by remember { mutableStateOf<Member_book?>(null) }
    BackHeader("账本成员", onBack)
    Text(book?.name ?: "账本", color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
    Text("本地版把成员关系写入设备数据库，保留创建者与成员角色，不连接远程服务。", color = palette.textSecondary)
    if (rows.isEmpty()) Empty("暂无成员记录", "加入共享账本后会出现在这里")
    rows.forEach { member ->
        val owner = member.role == ROLE_OWNER || member.member_id == LOCAL_MEMBER_ID
        GlassCard(onClick = { if (owner) onNotice("创建者不能被移除") else pending = member }) {
            Text(if (owner) "创建者" else "成员 #${member.member_id}", color = palette.textPrimary)
            Text(if (owner) "不可移除" else "点击移除", color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
    pending?.let { target ->
        ConfirmBar(
            "移除成员 #${target.member_id}？成员关系会被删除，账单不受影响。",
            onCancel = { pending = null },
            onConfirm = {
                if (repository.removeMember(bookId, target.member_id)) {
                    rows = repository.members(bookId)
                    onNotice("成员已移除")
                } else onNotice("创建者不能被移除")
                pending = null
            },
            confirmLabel = "移除",
        )
    }
}

private val BOOK_ICONS = listOf(1L to "账簿", 2L to "钱包", 3L to "餐饮", 4L to "旅行", 5L to "家庭", 6L to "心愿")
