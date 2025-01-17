package org.robok.engine.ui.screens.editor

/*
 *  This file is part of Robok © 2024.
 *
 *  Robok is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Robok is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with Robok.  If not, see <https://www.gnu.org/licenses/>.
 */

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.robok.engine.Strings
import org.robok.engine.core.components.toast.LocalToastHostState
import org.robok.engine.core.utils.SingleString
import org.robok.engine.extensions.navigation.navigateSingleTop
import org.robok.engine.feature.editor.RobokCodeEditor
import org.robok.engine.io.File
import org.robok.engine.manage.project.ProjectManager
import org.robok.engine.platform.LocalMainNavController
import org.robok.engine.routes.ProjectSettingsRoute
import org.robok.engine.ui.screens.editor.components.appbar.EditorTopBar
import org.robok.engine.ui.screens.editor.components.appbar.EditorTopBarAction
import org.robok.engine.ui.screens.editor.components.appbar.rememberEditorTopBarState
import org.robok.engine.ui.screens.editor.components.drawer.EditorDrawer
import org.robok.engine.ui.screens.editor.components.tab.EditorFileTabLayout
import org.robok.engine.ui.screens.editor.event.EditorEvent
import org.robok.engine.ui.screens.editor.viewmodel.EditorViewModel

@Composable
fun EditorScreen(pPath: String) {
  val context = LocalContext.current
  val editorViewModel = koinViewModel<EditorViewModel>().apply { this.context = context }
  val projectManager = ProjectManager(context).apply { this.projectPath = File(pPath) }
  editorViewModel.setProjectManager(projectManager)

  EditorDrawer(editorViewModel = editorViewModel) {
    EditorScreenContent(editorViewModel = editorViewModel)
  }
}

@Composable
private fun EditorScreenContent(editorViewModel: EditorViewModel) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val drawerState = LocalEditorFilesDrawerState.current
  val toastHostState = LocalToastHostState.current
  val navController = LocalMainNavController.current
  Scaffold(
    topBar = {
      EditorToolbar(
        editorViewModel = editorViewModel,
        onNavigationIconClick = { coroutineScope.launch { drawerState.open() } },
      )
    }
  ) { innerPadding ->
    LaunchedEffect(editorViewModel.editorEvent) {
      editorViewModel.editorEvent?.let { event ->
        when (event) {
          is EditorEvent.SelectFile -> editorViewModel.setCurrentFileIndex(event.index)
          is EditorEvent.OpenFile -> {
            handleFile(navController, editorViewModel, event.file)
            editorViewModel.clearEvent()
          }
          is EditorEvent.CloseFile -> editorViewModel.removeFile(event.index)
          is EditorEvent.CloseOthers -> editorViewModel.removeOthersFiles()
          is EditorEvent.CloseAll -> editorViewModel.removeAllFiles()
          is EditorEvent.SaveFile -> {
            editorViewModel.writeFile(
              editorViewModel.uiState.openedFiles.get(editorViewModel.uiState.selectedFileIndex)
            )
            coroutineScope.launch {
              toastHostState.showToast(
                message = context.getString(Strings.text_saved),
                icon = Icons.Rounded.Check,
              )
            }
          }
          is EditorEvent.SaveAllFiles -> {
            editorViewModel.writeAllFiles()
            coroutineScope.launch {
              toastHostState.showToast(
                message = context.getString(Strings.text_saved_all),
                icon = Icons.Rounded.Check,
              )
            }
          }
          is EditorEvent.Undo -> {
            editorViewModel.getSelectedEditor()?.let { editor ->
              editor.undo()
              editorViewModel.updateUndoRedo(editor)
            }
            editorViewModel.clearEvent()
          }
          is EditorEvent.Redo -> {
            editorViewModel.getSelectedEditor()?.let { editor ->
              editor.redo()
              editorViewModel.updateUndoRedo(editor)
            }
            editorViewModel.clearEvent()
          }
          is EditorEvent.More -> {
            editorViewModel.setMoreOptionOpen(!editorViewModel.uiState.moreOptionOpen)
            editorViewModel.clearEvent()
          }
        }
      }
    }
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (editorViewModel.uiState.hasFileOpen) {
        EditorFileTabLayout(editorViewModel = editorViewModel)
        Editor(editorViewModel = editorViewModel)
      } else {
        NoOpenedFilesContent()
      }
    }
  }
}

private fun handleFile(
  navController: NavHostController,
  editorViewModel: EditorViewModel,
  file: File,
) {
  val name = file.name
  when (name) {
    "config.json" -> {
      SingleString.instance.value = editorViewModel.projectManager.projectPath.path
      navController.navigateSingleTop(ProjectSettingsRoute)
    }
    else -> handleFileExtension(editorViewModel, file)
  }
}

private fun handleFileExtension(editorViewModel: EditorViewModel, file: File) {
  editorViewModel.addFile(file)
}

@Composable
private fun Editor(editorViewModel: EditorViewModel) {
  val uiState = editorViewModel.uiState
  val openedFiles = uiState.openedFiles
  val selectedFileIndex = uiState.selectedFileIndex
  val openedFile = openedFiles.getOrNull(selectedFileIndex)

  val selectedEditor = editorViewModel.getSelectedEditor()

  openedFile?.let { file ->
    selectedEditor?.let { editorView ->
      LaunchedEffect(editorView) { editorViewModel.updateUndoRedo(editorView) }
      key(file.path) { EditorView(editorView) }
    }
  }
}

@Composable
private fun EditorView(view: RobokCodeEditor) {
  AndroidView(
    factory = {
      view.apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
      }
    },
    modifier = Modifier.fillMaxSize(),
  )
}

@Composable
private fun NoOpenedFilesContent() {
  val drawerState = LocalEditorFilesDrawerState.current
  val coroutineScope = rememberCoroutineScope()
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = stringResource(id = Strings.text_no_files_open),
      style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(5.dp))
    ElevatedButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
      Text(text = stringResource(id = Strings.text_click_here_to_open_files))
    }
  }
}

@Composable
private fun EditorToolbar(
  editorViewModel: EditorViewModel,
  onNavigationIconClick: () -> Unit = {},
) {
  var topBarState = rememberEditorTopBarState()
  val uiState = editorViewModel.uiState
  val coroutineScope = rememberCoroutineScope()
  val toastHostState = LocalToastHostState.current
  topBarState =
    topBarState.copy(
      title = uiState.title,
      onNavigationIconClick = onNavigationIconClick,
      actions =
        listOf(
          EditorTopBarAction(
            name = stringResource(id = Strings.common_word_undo),
            icon = Icons.Rounded.Undo,
            enabled = uiState.canUndo,
            visible = uiState.hasFileOpen,
            onClick = { editorViewModel.undo() },
          ),
          EditorTopBarAction(
            name = stringResource(id = Strings.common_word_redo),
            icon = Icons.Rounded.Redo,
            enabled = uiState.canRedo,
            visible = uiState.hasFileOpen,
            onClick = { editorViewModel.redo() },
          ),
          EditorTopBarAction(
            name = stringResource(id = Strings.common_word_run),
            icon = Icons.Rounded.PlayArrow,
            onClick = {
              coroutineScope.launch {
                toastHostState.showToast(
                  message = "Not implemented yet",
                  icon = Icons.Rounded.Error,
                )
              }
            },
          ),
          EditorTopBarAction(
            name = stringResource(id = Strings.common_word_save),
            icon = Icons.Rounded.Save,
            visible = uiState.hasFileOpen,
            onClick = { editorViewModel.saveFile() },
          ),
          EditorTopBarAction(
            name = stringResource(id = Strings.common_word_more),
            icon = Icons.Rounded.MoreVert,
            visible = uiState.hasFileOpen,
            onClick = { editorViewModel.more() },
          ),
        ),
    )
  EditorTopBar(state = topBarState, editorViewModel = editorViewModel)
}
