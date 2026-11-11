package com.meow.ledger.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meow.ledger.shared.manage.CatalogRepository
import com.meow.ledger.shared.manage.DEFAULT_BOOK_ID
import com.meow.ledger.ui.auto.ActionButton
import com.meow.ledger.ui.auto.BackHeader
import com.meow.ledger.ui.theme.LocalPalette

/** 分类与标签：categories / category-edit:<id> / tags。 */
@Composable
fun CatalogPage(repository: CatalogRepository, page: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var notice by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            page == "categories" -> CategoryList(repository, onOpen, onBack) { notice = it }
            page.startsWith("category-edit:") -> CategoryEdit(repository, page.substringAfter(":").toLongOrNull(), onBack) { notice = it }
            page == "tags" -> TagList(repository, onBack) { notice = it }
        }
        if (notice.isNotEmpty()) Text(notice, color = LocalPalette.current.danger, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun CategoryList(repository: CatalogRepository, onOpen: (String) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var type by remember { mutableStateOf(1L) }
    var version by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    val rows = remember(type, version) { repository.categories(type) }
    BackHeader("分类", onBack)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("支出", type == 1L) { type = 1 }
        ChoiceChip("收入", type == 2L) { type = 2 }
        ChoiceChip("标签", false) { onOpen("tags") }
    }
    Text("点一个分类可以改名、隐藏或删除。内置分类只能隐藏。", color = palette.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
    rows.forEach { category ->
        GlassCard(onClick = { onOpen("category-edit:${category.id}") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.title, color = palette.textPrimary)
                Text(
                    when {
                        category.hidden == 1L -> "已隐藏"
                        category.is_custom == 1L -> "自定义"
                        else -> "内置"
                    },
                    color = palette.textSecondary,
                )
            }
        }
    }
    if (adding) {
        Field("新分类名称", title, { title = it }, "例如 宠物")
        ActionButton("保存", title.isNotBlank()) {
            val saved = repository.saveCategory(title.trim(), type, "")
            if (saved == null) onNotice("这个名称已经有了")
            else { title = ""; adding = false; version += 1; onNotice("分类已新增") }
        }
    } else ActionButton("新增分类", true) { adding = true }
}

@Composable
private fun CategoryEdit(repository: CatalogRepository, id: Long?, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val existing = remember(id) { id?.let(repository::category) }
    if (existing == null) { Missing("分类不存在", onBack); return }
    var title by remember { mutableStateOf(existing.title) }
    var confirm by remember { mutableStateOf(false) }
    val builtin = repository.isBuiltin(existing.id)
    BackHeader("编辑分类", onBack)
    Field("名称", title, { title = it }, "分类名称")
    ActionButton("保存", title.isNotBlank()) {
        if (repository.saveCategory(title.trim(), existing.type, existing.icon, existing.id) == null) onNotice("名称重复，没有保存")
        else { onNotice("已保存"); onBack() }
    }
    ActionButton(if (existing.hidden == 1L) "取消隐藏" else "隐藏分类", true) {
        repository.setHidden(existing.id, existing.hidden != 1L)
        onNotice(if (existing.hidden == 1L) "已取消隐藏" else "已隐藏，记账时不再出现")
        onBack()
    }
    if (builtin) Text("内置分类只能隐藏，不能删除。", color = LocalPalette.current.textSecondary, modifier = Modifier.padding(top = 8.dp))
    else if (confirm) ConfirmBar("删除分类「${existing.title}」？已记的账单仍保留原分类。", { confirm = false }, {
        if (repository.deleteCategory(existing.id)) onBack() else onNotice("内置分类不能删除")
    }, "删除")
    else ActionButton("删除分类", true) { confirm = true }
}

@Composable
private fun TagList(repository: CatalogRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    val palette = LocalPalette.current
    var version by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(0L) }
    var pending by remember { mutableStateOf(0L) }
    val tags = remember(version) { repository.tags(DEFAULT_BOOK_ID) }
    BackHeader("标签", onBack)
    Text("标签按账本保存。这里管理默认账本的标签，排序会一并保存。", color = palette.textSecondary)
    if (tags.isEmpty()) Empty("还没有标签", "加一个，记账时就能选")
    tags.forEachIndexed { index, tag ->
        if (editing == tag.tag_id) {
            Field("标签名称", name, { name = it }, "例如 可报销")
            ActionButton("保存", name.isNotBlank()) {
                if (repository.saveTag(name.trim(), DEFAULT_BOOK_ID, tag.tag_id) == null) onNotice("名称重复")
                else { editing = 0; version += 1; onNotice("标签已修改") }
            }
        } else GlassCard {
            Text(tag.name, color = palette.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (index > 0) ChoiceChip("上移", false) { move(repository, tags.map { it.tag_id }, index, -1); version += 1 }
                if (index < tags.lastIndex) ChoiceChip("下移", false) { move(repository, tags.map { it.tag_id }, index, 1); version += 1 }
                ChoiceChip("改名", false) { editing = tag.tag_id; name = tag.name }
                ChoiceChip("删除", false) { pending = tag.tag_id }
            }
        }
        if (pending == tag.tag_id) ConfirmBar("删除标签「${tag.name}」？已记账单上的这个标签会一起消失。", { pending = 0 }, {
            repository.deleteTag(tag.tag_id)
            pending = 0
            version += 1
            onNotice("标签已删除")
        }, "删除")
    }
    Field("新标签", name.takeIf { editing == 0L } ?: "", { if (editing == 0L) name = it }, "例如 可报销")
    ActionButton("新增标签", editing == 0L && name.isNotBlank()) {
        if (repository.saveTag(name.trim(), DEFAULT_BOOK_ID) == null) onNotice("名称重复")
        else { name = ""; version += 1; onNotice("标签已新增") }
    }
}

private fun move(repository: CatalogRepository, ids: List<Long>, index: Int, delta: Int) {
    val next = ids.toMutableList()
    val swap = next[index]
    next[index] = next[index + delta]
    next[index + delta] = swap
    repository.sortTags(next, DEFAULT_BOOK_ID)
}
